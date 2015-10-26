package za.org.grassroot.core.util;

/**
 * Created by aakilomar on 10/25/15.
 */
public class FormatUtil {

    /*
    This method drop trailing zeroes and the decimal if only zeroes after the decimal, I hope
     */
    public static String formatDoubleToString(double d)
    {
        if(d == (long) d)
            return String.format("%d",(long)d);
        else
            return String.format("%s",d);
    }
}
