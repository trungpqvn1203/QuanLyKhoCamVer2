package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashEntrySource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đọc sổ quỹ từ VIEW cash_flow_entry (V19) bằng SQL thuần: VIEW gộp 5 bảng (UNION ALL) không có entity JPA, và số dư
 * lũy kế cần hàm cửa sổ SUM(...) OVER (...) — viết SQL trực tiếp rõ hơn JPQL.
 * Thứ tự một ngày: theo thời điểm ghi (created_at), rồi nguồn + id để thứ tự luôn xác định, số dư lũy kế không nhảy.
 */
@Repository
@RequiredArgsConstructor
public class CashLedgerRepository {

    private static final String ORDER = "entry_date, created_at, source, source_id";

    private final NamedParameterJdbcTemplate jdbc;

    /** Tổng số tiền có dấu của tài khoản trong [from, to] (hai đầu đều tính). */
    public BigDecimal sum(CashAccount account, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM cash_flow_entry
                WHERE account = :account AND entry_date BETWEEN :from AND :to
                """, params(account, from, to), BigDecimal.class);
    }

    /** Tổng thu (+) và tổng chi (− đổi dấu) của tài khoản trong [from, to]. */
    public InOut inOut(CashAccount account, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0)  AS total_in,
                       COALESCE(-SUM(amount) FILTER (WHERE amount < 0), 0) AS total_out
                FROM cash_flow_entry
                WHERE account = :account AND entry_date BETWEEN :from AND :to
                """, params(account, from, to),
                (rs, rowNum) -> new InOut(rs.getBigDecimal("total_in"), rs.getBigDecimal("total_out")));
    }

    /**
     * Các dòng sổ quỹ của tài khoản trong [from, to]. cumulative = tổng có dấu từ đầu khoảng tới hết dòng đó
     * (service cộng thêm số dư đầu khoảng). Lọc theo nguồn thì số dư lũy kế mất nghĩa → cumulative = null.
     */
    public List<Row> entries(CashAccount account, LocalDate from, LocalDate to, CashEntrySource source) {
        MapSqlParameterSource params = params(account, from, to);
        String cumulative = source == null ? "SUM(amount) OVER (ORDER BY " + ORDER + ")" : "NULL::numeric";
        String sourceFilter = "";
        if (source != null) {
            sourceFilter = " AND source = :source";
            params.addValue("source", source.name());
        }
        return jdbc.query("""
                SELECT source, source_id, account, entry_date, amount, reference, counterparty, category, note,
                       is_reversal, reversed, created_at, %s AS cumulative
                FROM cash_flow_entry
                WHERE account = :account AND entry_date BETWEEN :from AND :to%s
                ORDER BY %s
                """.formatted(cumulative, sourceFilter, ORDER), params, CashLedgerRepository::row);
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                CashEntrySource.valueOf(rs.getString("source")),
                rs.getLong("source_id"),
                CashAccount.valueOf(rs.getString("account")),
                rs.getObject("entry_date", LocalDate.class),
                rs.getBigDecimal("amount"),
                rs.getString("reference"),
                rs.getString("counterparty"),
                rs.getString("category"),
                rs.getString("note"),
                rs.getBoolean("is_reversal"),
                rs.getBoolean("reversed"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBigDecimal("cumulative"));
    }

    private static MapSqlParameterSource params(CashAccount account, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("account", account.name())
                .addValue("from", from)
                .addValue("to", to);
    }

    public record InOut(BigDecimal totalIn, BigDecimal totalOut) {
    }

    public record Row(CashEntrySource source, long sourceId, CashAccount account, LocalDate entryDate,
                      BigDecimal amount, String reference, String counterparty, String category, String note,
                      boolean reversal, boolean reversed, Instant createdAt, BigDecimal cumulative) {
    }
}
