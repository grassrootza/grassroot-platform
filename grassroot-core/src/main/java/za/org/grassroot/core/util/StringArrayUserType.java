package za.org.grassroot.core.util;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.Arrays;

/**
 * Created by luke on 2017/05/05.
 */
public class StringArrayUserType<T extends Serializable> implements UserType {

    protected static final int[] SQL_TYPES = { Types.ARRAY };
    private  Class<T> typeParameterClass;

    @Override
    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return this.deepCopy(cached);
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value == null ? null : ((Object[]) value).clone();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        return (T) this.deepCopy(value);
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        if (x == null) {
            return y == null;
        }
        return (x instanceof String[] && y instanceof String[]) ?
                Arrays.equals((String[]) x, (String[]) y) : x.equals(y);
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Object nullSafeGet(ResultSet resultSet, String[] names, SessionImplementor session, Object owner)
            throws HibernateException, SQLException {
        if (resultSet.wasNull()) {
            return null;
        }
        if (resultSet.getArray(names[0]) == null) {
            return new String[0];
        }

        Array array = resultSet.getArray(names[0]);
        @SuppressWarnings("unchecked")
        T javaArray = (T) array.getArray();
        return javaArray;
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int index, SessionImplementor session)
            throws HibernateException, SQLException {
        Connection connection = statement.getConnection();
        if (value == null) {
            statement.setNull(index, SQL_TYPES[0]);
        } else {
            @SuppressWarnings("unchecked")
            T castObject = (T) value;
            Array array = connection.createArrayOf("text", (Object[]) castObject);
            statement.setArray(index, array);
        }
    }

    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        return original;
    }

    @Override
    public Class<T> returnedClass() {
        return typeParameterClass;
    }

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.ARRAY };
    }
}