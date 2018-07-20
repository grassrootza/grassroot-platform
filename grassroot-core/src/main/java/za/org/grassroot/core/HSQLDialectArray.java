package za.org.grassroot.core;

import org.hibernate.dialect.HSQLDialect;

import java.sql.Types;

/**
 * Created by luke on 2017/05/05.
 */
public class HSQLDialectArray extends HSQLDialect {

    public HSQLDialectArray() {
        super();

        registerHibernateType(Types.ARRAY, "array");
        registerColumnType(Types.ARRAY, "ARRAY");
    }

}
