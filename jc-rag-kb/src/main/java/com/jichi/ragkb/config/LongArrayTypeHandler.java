package com.jichi.ragkb.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler：Long[] ↔ PostgreSQL BIGINT[]
 * 写入时通过 Connection.createArrayOf 创建 SQL 数组，
 * 读取时通过 ResultSet.getArray 获取 Java 数组
 */
@MappedTypes(Long[].class)
public class LongArrayTypeHandler extends BaseTypeHandler<Long[]> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long[] parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("bigint", parameter);
        ps.setArray(i, array);
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLongArray(rs.getArray(columnName));
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLongArray(rs.getArray(columnIndex));
    }

    @Override
    public Long[] getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws SQLException {
        return toLongArray(cs.getArray(columnIndex));
    }

    private Long[] toLongArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object obj = array.getArray();
        if (obj instanceof Long[] longs) {
            return longs;
        }
        if (obj instanceof Object[] objects) {
            Long[] result = new Long[objects.length];
            for (int i = 0; i < objects.length; i++) {
                result[i] = ((Number) objects[i]).longValue();
            }
            return result;
        }
        return null;
    }
}