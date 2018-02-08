package za.org.grassroot.core.enums;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Province {

    ZA_GP,
    ZA_NC,
    ZA_WC,
    ZA_EC,
    ZA_KZN,
    ZA_LP,
    ZA_NW,
    ZA_FS,
    ZA_MP,
    INTL;

    // not doing this as strings and to string, as introduces many issues, and we will probably want multi-lingual in future
    public static final Map<String, Province> EN_PROVINCE_NAMES = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<>("gauteng", ZA_GP),
            new AbstractMap.SimpleEntry<>("western cape", ZA_WC),
            new AbstractMap.SimpleEntry<>("eastern cape", ZA_EC),
            new AbstractMap.SimpleEntry<>("northern cape", ZA_NC),
            new AbstractMap.SimpleEntry<>("kwazulu natal", ZA_KZN),
            new AbstractMap.SimpleEntry<>("limpopo", ZA_LP),
            new AbstractMap.SimpleEntry<>("north west", ZA_NW),
            new AbstractMap.SimpleEntry<>("north-west", ZA_NW),
            new AbstractMap.SimpleEntry<>("free state", ZA_FS),
            new AbstractMap.SimpleEntry<>("free-state", ZA_FS),
            new AbstractMap.SimpleEntry<>("Mpumalanga", ZA_MP),
            new AbstractMap.SimpleEntry<>("foreign", INTL),
            new AbstractMap.SimpleEntry<>("abroad", INTL),
            new AbstractMap.SimpleEntry<>("overseas", INTL)
    ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    public static final Map<Province, String> CANONICAL_NAMES_ZA = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<>(ZA_GP, "Gauteng"),
            new AbstractMap.SimpleEntry<>(ZA_WC, "Western Cape"),
            new AbstractMap.SimpleEntry<>(ZA_EC, "Eastern Cape"),
            new AbstractMap.SimpleEntry<>(ZA_NC, "Northern Cape"),
            new AbstractMap.SimpleEntry<>(ZA_KZN, "KwaZulu Natal"),
            new AbstractMap.SimpleEntry<>(ZA_LP, "Limpopo"),
            new AbstractMap.SimpleEntry<>(ZA_NW, "North West"),
            new AbstractMap.SimpleEntry<>(ZA_FS, "Free State")
    ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

}