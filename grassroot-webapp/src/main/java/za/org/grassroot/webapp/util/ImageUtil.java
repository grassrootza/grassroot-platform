package za.org.grassroot.webapp.util;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.util.UIDGenerator;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by paballo on 2016/07/09.
 */
public class ImageUtil {


    private static final String RELATIVE_PATH = "/image/get?imageId=";


    public static String generateFileName(MultipartFile file, HttpServletRequest request) {

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) {
            builder.append(":").append(serverPort);
        }

        String mimeTypeExtension = getMimiTypeExtension(file.getContentType());
        builder.append(RELATIVE_PATH)
                .append(UIDGenerator.generateId())
                .append(".")
                .append(mimeTypeExtension);

        return builder.toString();

    }

    public static String getMimiTypeExtension(String mimeType) {

        if (!(mimeType.endsWith("png") || mimeType.endsWith("jpg") || mimeType.endsWith("jpeg"))) {
            throw new IllegalArgumentException("Invalid file format Exception");
        }
        String mimeTypeExtension = mimeType.substring(mimeType.indexOf("/") + 1, mimeType.length());
        mimeTypeExtension = mimeTypeExtension.equals("jpeg") ? "jpg" : mimeTypeExtension;

        return mimeTypeExtension;
    }

    public static String getMimeType(String imageUrl) {
        //for now images in the database guaranteed to be either in jpeg or png format
        if (!(imageUrl.endsWith("png") || imageUrl.endsWith("jpg"))) {
            return "image/jpeg";
        }
        return "image/png";

    }

    public static String getRelativePath(){
        return RELATIVE_PATH;
    }
}
