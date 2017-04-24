package za.org.grassroot.integration.location.aatmodels;

/**
 * Created by luke on 2017/04/24.
 * todo: create these from wdsl instead and/or abstract them
 */
public class AllowedMsisdnResponse {

    public boolean isSuccessful() { return true; }

    public int getResultCode() { return 100; }

    public String getMessage() { return "Hello"; }

}
