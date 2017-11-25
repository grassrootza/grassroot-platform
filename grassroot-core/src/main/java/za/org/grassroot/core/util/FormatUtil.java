package za.org.grassroot.core.util;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static String removeUnwantedCharacters(String content) {
        if (content == null) {
            return null;
        }

        String contentToReturn = "";

        try{
            byte[] contentBytes = content.getBytes("UTF-8");
            contentToReturn = new String(contentBytes, "UTF-8");
        }catch (UnsupportedEncodingException e){
            e.printStackTrace();
        }

        Pattern unicodeOutliers = Pattern.compile("[\ud83c\udc00-\ud83c\udfff]|[\ud83d\udc00-\ud83d\udfff]|[\u2600-\u27ff]",
                Pattern.UNICODE_CASE |
                        Pattern.CANON_EQ |
                        Pattern.CASE_INSENSITIVE);
        Matcher unicodeOutlierMatcher = unicodeOutliers.matcher(contentToReturn);

        contentToReturn = unicodeOutlierMatcher.replaceAll("");
        return contentToReturn;
    }
}
