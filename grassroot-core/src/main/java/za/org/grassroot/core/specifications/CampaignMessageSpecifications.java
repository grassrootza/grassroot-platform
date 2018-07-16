package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessage_;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.Locale;

public final class CampaignMessageSpecifications {

    public static Specification<CampaignMessage> ofTypeForCampaign(Campaign campaign, CampaignActionType actionType,
                                                             Locale locale, UserInterfaceType channel, MessageVariationAssignment variation) {
        return Specification.where(activeForCampaign(campaign))
                .and(ofActionType(actionType))
                .and(withUserInterfaceParams(locale, channel, variation));
    }

    public static Specification<CampaignMessage> ofTypeForCampaign(Campaign campaign, CampaignActionType actionType, Locale locale) {
        return Specification.where(activeForCampaign(campaign)).and(ofActionType(actionType))
                .and(forLanguage(locale));
    }

    private static Specification<CampaignMessage> activeForCampaign(Campaign campaign) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("campaign"), campaign), cb.isTrue(root.get(CampaignMessage_.active)));
    }

    private static Specification<CampaignMessage> ofActionType(CampaignActionType actionType) {
        return (root, query, cb) -> cb.equal(root.get("actionType"), actionType);
    }

    private static Specification<CampaignMessage> forLanguage(Locale locale) {
        return (root, query, cb) -> cb.equal(root.get(CampaignMessage_.locale), locale);
    }

    private static Specification<CampaignMessage> withUserInterfaceParams(Locale locale, UserInterfaceType channel,
                                                                   MessageVariationAssignment variation) {
        return (root, query, cb) -> cb.and(cb.and(cb.equal(root.get("locale"), locale), cb.equal(root.get("channel"), channel)),
                cb.equal(root.get("variation"), variation));
    }

}
