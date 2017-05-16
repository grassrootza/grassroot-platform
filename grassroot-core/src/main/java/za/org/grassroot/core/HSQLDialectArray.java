package za.org.grassroot.core;

import org.hibernate.dialect.HSQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;

/**
 * Created by luke on 2017/05/05.
 */
public class HSQLDialectArray extends HSQLDialect {

    private static final Logger log = LoggerFactory.getLogger(HSQLDialectArray.class);

    public HSQLDialectArray() {
        super();

        registerHibernateType(Types.ARRAY, "array");
        registerColumnType(Types.ARRAY, "ARRAY");
    }

}
