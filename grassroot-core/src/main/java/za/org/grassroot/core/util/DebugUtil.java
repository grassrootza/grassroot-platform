package za.org.grassroot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by luke on 2017/02/01.
 * Helper class for double checking transaction boundaries
 */
public class DebugUtil {

    private static final Logger logger = LoggerFactory.getLogger(DebugUtil.class);

    private static final boolean transactionDebugging = true;
    private static final boolean verboseTransactionDebugging = false;

    private static void showTransactionStatus(String message) {
        logger.info(((transactionActive()) ? "[+] " : "[-] ") + message);
    }

    @SuppressWarnings("unchecked")
    private static boolean transactionActive() {
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Class tsmClass = contextClassLoader.loadClass("org.springframework.transaction.support.TransactionSynchronizationManager");
            return (Boolean) tsmClass.getMethod("isActualTransactionActive", (Class<?>[]) null).invoke(null, (Object[]) null);
        } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException | SecurityException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }

        // If we got here it means there was an exception
        throw new IllegalStateException("DebugUtils.transactionActive was unable to complete properly");
    }

    public static void transactionRequired(String message) {
        if (!transactionDebugging) {
            return;
        }

        if (verboseTransactionDebugging) {
            showTransactionStatus(message);
        }

        if (!transactionActive()) {
            throw new IllegalStateException("Transaction required but not active [" + message + "]");
        }
    }

}
