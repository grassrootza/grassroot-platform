package za.org.grassroot.services.account;

import za.org.grassroot.core.dto.GrassrootEmail;

/**
 * Created by luke on 2017/03/01.
 */
public interface AccountEmailService {

    GrassrootEmail generateDonationShareEmail(String fromName, String toAddress, String linkToDonate);

}
