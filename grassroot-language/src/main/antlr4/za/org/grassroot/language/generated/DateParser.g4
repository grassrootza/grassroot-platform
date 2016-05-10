parser grammar DateParser;
import NumericRules;
options {
  tokenVocab=DateLexer;
  language=Java;
}
tokens {
  MONTH_OF_YEAR,
  DAY_OF_MONTH,
  DAY_OF_WEEK,
  DAY_OF_YEAR,
  YEAR_OF,
  DATE_TIME,
  DATE_TIME_ALTERNATIVE,
  EXPLICIT_DATE,
  RELATIVE_DATE,
  SEEK,
  DIRECTION,
  SEEK_BY,
  EXPLICIT_SEEK,
  SPAN,
  EXPLICIT_TIME,
  RELATIVE_TIME,
  HOURS_OF_DAY,
  MINUTES_OF_HOUR,
  SECONDS_OF_MINUTE,
  AM_PM,
  ZONE,
  ZONE_OFFSET,
  RECURRENCE,
  HOLIDAY,
  SEASON
}


@members {
  private org.slf4j.Logger _logger =
    org.slf4j.LoggerFactory.getLogger(za.org.grassroot.language.generated.DateParser.class);

  /*@Override
  public void displayRecognitionError(String[] tokenNames, RecognitionException re) {
    String message = getErrorHeader(re);
    try { message += getErrorMessage(re, tokenNames); } catch(Exception e) {}
    _logger.debug(message);
  }*/
}

parse
  : empty (recurrence | date_time_alternative) #parsingEntryPoint
  ;
  
recurrence
  : EVERY WHITE_SPACE date_time_alternative (WHITE_SPACE UNTIL WHITE_SPACE date_time)?      #recurrenceRule
  ;

empty
  :                                                                                         #emptyRule
  ;

date_time
  : (date (date_time_separator explicit_time)? | explicit_time (time_date_separator date)?) #date_timeRule1
  | relative_time #date_timeRule2
  ;

date_time_separator
  : WHITE_SPACE? (AT WHITE_SPACE)?                                                          #date_time_separatorRule1
  | WHITE_SPACE? COMMA WHITE_SPACE? (AT WHITE_SPACE)?                                       #date_time_separatorRule2
  | T                                                                                       #date_time_separatorRule3
  ;

time_date_separator
  : WHITE_SPACE ((ON | OF) WHITE_SPACE)?                                                    #time_date_separatorRule1
  | COMMA WHITE_SPACE? ((ON | OF) WHITE_SPACE)?                                             #time_date_separatorRule2
  ;

date
  : formal_date                                                                             #formal_dateRule
  | relaxed_date                                                                            #relaxed_dateRule
  | relative_date                                                                           #relaxed_dateRule
  | explicit_relative_date                                                                  #explicit_relative_dateRule
  | global_date_prefix WHITE_SPACE date                                                     #global_date_prefix_dateRule
  ;

date_time_alternative
  // for 3 days, for 7 months, for twenty seconds
  : date_time_alternative_range                                                             #date_time_alternative_rangeRule

 // today or the day after that, feb 16th or 2 days after that, january fourth or the friday after
  | date conjunction global_date_prefix (WHITE_SPACE THAT)? (date_time_separator explicit_time)? #todayOrAfterThatRule

  // first or last day of 2009
  | alternative_day_of_year_list                                                            #alternative_day_of_year_listRule

  // feb 16, 17, or 18
  | alternative_day_of_month_list                                                           #alternative_day_of_month_listRule

  // "next wed or thurs" , "next wed, thurs, or fri", "this wed or next"
  | alternative_day_of_week_list                                                            #alternative_day_of_week_listRule

  // 1/2 or 1/4 or 1/6 at 6pm, Aug 16 at 10am or Sept 28th at 11am, Feb 28th
  | date_time (conjunction date_time)*                                                      #date_timeConjuctionRule
  ;

date_time_alternative_range
  : (
    // in two days, in 3 or 4 days
    range_direction one=spelled_or_int_optional_prefix
      (conjunction two=spelled_or_int_optional_prefix)? WHITE_SPACE range_span

    // two to 7 days, 4 to 5 days ago
    | one=spelled_or_int_optional_prefix conjunction two=spelled_or_int_optional_prefix WHITE_SPACE
      range_span (WHITE_SPACE relative_date_suffix)?
  )                                                                                         #date_time_alternative_rangeRul1
  ;

range_direction returns [Boolean inclusive]
  : (FOR | NEXT) WHITE_SPACE  {$inclusive=true;}                                            #range_directionRule1
  | (LAST | PAST) WHITE_SPACE {$inclusive=true;}                                            #range_directionRule2
  | IN WHITE_SPACE            {$inclusive=false;}                                           #range_directionRule3
  ;

conjunction
  : COMMA? WHITE_SPACE (AND | OR | TO | THROUGH | DASH) WHITE_SPACE                         #conjunctionRule1
  ;

range_span
  : relative_date_span                                                                      #range_spanRule1
  | relative_time_span                                                                      #relative_time_spanRule
  ;

alternative_day_of_year_list
  : first=explicit_day_of_year_part conjunction second=explicit_day_of_year_part WHITE_SPACE relaxed_year
                                                                                            #altDayOfYearListRule
  ;

alternative_day_of_month_list
  // mon may 15 or tues may 16
  : ((relaxed_day_of_week? relaxed_month WHITE_SPACE relaxed_day_of_month (conjunction relaxed_day_of_month)+) (date_time_separator explicit_time)?)
                                                                                            #altDayOfMonthListRule1
  | first=explicit_day_of_month_part conjunction second=explicit_day_of_month_part WHITE_SPACE alternative_day_seek (date_time_separator explicit_time)?
                                                                                            #altDayOfMonthRule2
  ;

alternative_day_seek
  //  next september
  : prefix WHITE_SPACE explicit_relative_month                                              #altDaySeekRule1

  //  2 septembers from now
  | spelled_or_int_optional_prefix WHITE_SPACE explicit_relative_month WHITE_SPACE relative_date_suffix
                                                                                            #altDaySeekRule2

  //  september
  | relaxed_month                                                                           #altDaySeekRule3
  ;

alternative_day_of_week_list
  : first_direction=alternative_direction WHITE_SPACE day_of_week
    // this wed or thursday or friday
    ((day_of_week_list_separator day_of_week)+ (date_time_separator explicit_time)?

    // this wed. or next
    | conjunction second_direction=alternative_direction (date_time_separator explicit_time)?
    )                                                                                       #altDaySeekListRule1
  ;

day_of_week_list_separator
  : COMMA (WHITE_SPACE | conjunction)                                                       #dayOfWeekListRule1
  | conjunction                                                                             #dayOfWeekListRule2
  ;

alternative_direction
  : NEXT                                                                                    #nextRule
  | LAST                                                                                    #lastRule
  | THIS                                                                                    #thisRule
  ;

global_date_prefix
  // the day after
  : (THE WHITE_SPACE)? DAY WHITE_SPACE prefix_direction                                     #gdpRule1

  // 2 weeks from now
  | (global_date_prefix_amount WHITE_SPACE)? global_date_prefix_seek prefix_direction       #gdpRule2

  // 3 fridays before, the friday after, 2 fridays from now
  | (THE WHITE_SPACE)? (global_date_prefix_amount WHITE_SPACE)? day_of_week WHITE_SPACE prefix_direction
                                                                                            #dgpRule3
  ;

global_date_prefix_amount
  : spelled_first_to_thirty_first                                                           #gdpaRule1
  | spelled_or_int_optional_prefix                                                          #gdpaRule2
  ;

global_date_prefix_seek
  : DAY WHITE_SPACE                                                                         #dayRule
  | WEEK WHITE_SPACE                                                                        #weekRule
  | MONTH WHITE_SPACE                                                                       #monthRule
  | YEAR WHITE_SPACE                                                                        #yearRule
  ;

prefix_direction
  : (AFTER | FROM | ON)                                                                     #pdAFORule
  | BEFORE                                                                                  #beforeRule
  ;

// ********** relaxed date rules **********

// relaxed date with a spelled-out or abbreviated month
relaxed_date
  : relaxed_date_month_first                                                                #rdmfRule
  | relaxed_date_month_last                                                                 #rdmlRule
  ;

relaxed_date_month_first
  : relaxed_day_of_week? relaxed_month COMMA? WHITE_SPACE relaxed_day_of_month (relaxed_year_prefix relaxed_year)?
                                                                                            #rdmfDefRule1
  | relaxed_day_of_week? relaxed_month COMMA? (WHITE_SPACE | formal_date_separator) relaxed_day_of_month WHITE_SPACE? (relaxed_year_prefix relaxed_year)?
                                                                                            #rmdfDefRule2
  ;

relaxed_date_month_last
  : relaxed_day_of_week? relaxed_day_of_month_prefix? relaxed_day_of_month
      WHITE_SPACE (OF WHITE_SPACE)? relaxed_month (relaxed_year_prefix relaxed_year)?  #rdmlDefRule
  ;

relaxed_day_of_week
  : (prefix WHITE_SPACE)? day_of_week COMMA? WHITE_SPACE?  #rdowRule
  ;

relaxed_day_of_month_prefix
  : (THE WHITE_SPACE)  #rdompRule1
  | (COMMA WHITE_SPACE?) #rdompRule2
  ;

relaxed_month
  : JANUARY #janRule
  | FEBRUARY #febRule
  | MARCH #marRule
  | APRIL #aprRule
  | MAY #mayRule
  | JUNE #junRule
  | JULY #julRule
  | AUGUST #augRule
  | SEPTEMBER #septRule
  | OCTOBER #octRule
  | NOVEMBER #novRule
  | DECEMBER #decRule
  ;

relaxed_day_of_month
  : spelled_or_int_01_to_31_optional_prefix #rdomsoiRule
  | spelled_first_to_thirty_first #rdomfttfRule
  ;

// TODO expand these to 366
relaxed_day_of_year
  : spelled_or_int_01_to_31_optional_prefix #rdoysoiRule
  | spelled_first_to_thirty_first #rdoysfttfRule
  ;

relaxed_year
  : SINGLE_QUOTE int_00_to_99_mandatory_prefix #rymandprefRule
  | int_four_digits #ryifdRule
  ;

relaxed_year_prefix
  : (COMMA WHITE_SPACE? | WHITE_SPACE) (IN WHITE_SPACE THE WHITE_SPACE YEAR WHITE_SPACE)? #rypRule
  ;

// ********** formal date rules **********

formal_date
  // march 2009
  : relaxed_month WHITE_SPACE relaxed_year #fdrmryRule

  // year first: 1979-02-28, 1980/01/02, etc.  full 4 digit year required to match
  | relaxed_day_of_week? formal_year_four_digits formal_date_separator formal_month_of_year formal_date_separator formal_day_of_month #fdyearFirstRule

  // year last: 1/02/1980, 2/28/79.  2 or 4 digit year is acceptable
  | relaxed_day_of_week? formal_day_of_month formal_date_separator formal_month_of_year  (formal_date_separator formal_year)? #fdyearLastRule

  // 15-Apr-2014
  | formal_day_of_month formal_date_separator relaxed_month (formal_date_separator formal_year_four_digits)? #fdSAStyleRule
  ;

formal_month_of_year
  : int_01_to_12_optional_prefix #fmoyItopRule
  ;

formal_day_of_month
  : int_01_to_31_optional_prefix #fdomItopRule
  ;

formal_year
  : formal_year_four_digits #fyFyfdRule
  | int_00_to_99_mandatory_prefix #itopMandRule
  ;

formal_year_four_digits
  : int_four_digits #fyfdIfdRule
  ;

formal_date_separator
  : DASH   #dashRule
  | SLASH #slashRule
  ;

// ********** relative date rules **********

relative_date
  // next wed, last month
  : relative_date_prefix WHITE_SPACE relative_target (WHITE_SPACE spelled_or_int_optional_prefix WHITE_SPACE relative_date_span)* #rdRdpRule

  // this month, this week
  | implicit_prefix WHITE_SPACE relative_target #rdIpRule

  // monday, tuesday
  | day_of_week #rdDowRule
      // relative target with no prefix has an implicit seek of 0

  // january, february
  | relaxed_month #rdRmRule

  // one month from now
  | spelled_or_int_optional_prefix WHITE_SPACE relative_target WHITE_SPACE relative_date_suffix #rdSoipRule

  | one=spelled_or_int_optional_prefix WHITE_SPACE relative_target (WHITE_SPACE two+=spelled_or_int_optional_prefix WHITE_SPACE relative_date_span)+ WHITE_SPACE relative_date_suffix #rdSoipRule

  // a month from now
  | relative_target WHITE_SPACE relative_date_suffix #rdRtRule

  // the week after next
  | (THE WHITE_SPACE)? relative_date_span WHITE_SPACE AFTER WHITE_SPACE NEXT #rdRdsRule

  // today, tomorrow
  | named_relative_date #rdNrdRule

  // next christmas, 2 thanksgivings ago
  | holiday #rdHolRule

  // next fall, 2 summers from now
  | season #rdSeaRule
  ;

// ********** explicit relative date rules **********
// these represent explicit points within a relative range

explicit_relative_date
  // the first day of 2009
  : explicit_day_of_year_part WHITE_SPACE relaxed_year #xRdXdoyRule

  | explicit_day_of_month_part WHITE_SPACE explicit_relative_month_seek (relaxed_year_prefix relaxed_year)? #xRdXdomRule

  | explicit_day_of_week_part WHITE_SPACE explicit_relative_week_seek #xRdXdowRule
  ;

explicit_relative_month_seek
  // 1st of three months ago, 10th of 3 octobers from now, the last monday in 2 novembers ago
  : spelled_or_int_optional_prefix WHITE_SPACE explicit_relative_month WHITE_SPACE relative_date_suffix #xRmSoipRule

  // 10th of next month, 31st of last month, 10th of next october, 30th of this month, the last thursday of last november
  | prefix WHITE_SPACE explicit_relative_month #xRmPrefRule

  // 10th of the month after next
  | THE WHITE_SPACE MONTH WHITE_SPACE AFTER WHITE_SPACE NEXT #xRmManRule

  // september
  | relaxed_month #xRmRmRule
  ;

explicit_relative_week_seek
  // after next
  : AFTER WHITE_SPACE NEXT #xRwsAnRule

  // before last
  | BEFORE WHITE_SPACE LAST #xRwsBlRule

  // last week, tuesday of next week
  | prefix WHITE_SPACE WEEK #xRwsPrefRule

  // 2 weeks ago, tuesday of 3 weeks from now
  | spelled_or_int_optional_prefix WHITE_SPACE WEEK WHITE_SPACE relative_date_suffix #xRwsSoipRule

  // the week after next
  | THE WHITE_SPACE WEEK WHITE_SPACE AFTER WHITE_SPACE NEXT #xRwsWanRule
  ;

explicit_day_of_month_part
  // first of, 10th of, 31st of,
  : (THE WHITE_SPACE)? relaxed_day_of_month day_of_month_suffix? #xDomFoRule

  // the last thursday
  | (THE WHITE_SPACE)? relative_occurrence_index WHITE_SPACE day_of_week day_of_month_suffix #xDomLoRule

  // in the start of, at the beginning of, the end of, last day of, first day of
  | (((IN | AT) WHITE_SPACE)? THE WHITE_SPACE)? explicit_day_of_month_bound day_of_month_suffix? #xDomInAtRule
  ;

day_of_month_suffix
  : WHITE_SPACE (IN | OF) (WHITE_SPACE MONTH)? #domSufRule
  ;

explicit_day_of_week_part
  // monday of, tuesday of
  : (THE WHITE_SPACE)? relaxed_day_of_week (IN | OF)? #xDowPartRule

  // in the end of, at the beginning of
  | (((IN | AT) WHITE_SPACE)? THE WHITE_SPACE)? explicit_day_of_week_bound WHITE_SPACE (OF | IN) #xDowInAtRule
  ;

explicit_day_of_year_part
  // last of, first of, 15th day of
  : (THE WHITE_SPACE)? relaxed_day_of_year (WHITE_SPACE (IN | OF))? #xDoyPartRule

  // in the start of, at the beginning of, the end of, last day of, first day of
  | (((IN | AT) WHITE_SPACE)? THE WHITE_SPACE)? explicit_day_of_year_bound (WHITE_SPACE (OF | IN))? #xDoyInAtRule
  ;

// the lower or upper bound when talking about days in a year
explicit_day_of_year_bound
  // beginning, start
  : (BEGINNING | START) #xDoyBbeginRule

  // first day, 2nd day, etc
  | (spelled_first_to_thirty_first WHITE_SPACE DAY) #xDoyBSfttfRule

  // end, last day
  | (END | (LAST WHITE_SPACE DAY)) #xDoyEndRule
  ;

// the lower or upper bound when talking about days in a month
explicit_day_of_month_bound
  // beginning, start
  : (BEGINNING | START) #xDomBBeginRule

  // first day, 2nd day, etc
  | (spelled_first_to_thirty_first WHITE_SPACE DAY) #xDomBSfttfRule

  // end, last day
  | (END | (LAST WHITE_SPACE DAY)) #xDomBEndRule
  ;

// the lower or upper bound when talking about the days in a week:
explicit_day_of_week_bound
  // beginning, start, first day
  : (BEGINNING | START | (FIRST WHITE_SPACE DAY)) #xDowBBeginRule

  // end, last day
  | (END | (LAST WHITE_SPACE DAY)) #xDowBEndRule
  ;

explicit_relative_month
  : relaxed_month #xRmRelRule
  | MONTH #xRmMonRule
  ;

relative_occurrence_index
  : (FIRST  | INT_1 ST?) #rOccInFirRule
  | (SECOND | INT_2 ND?) #rOccInSecRule
  | (THIRD  | INT_3 RD?) #rOccInThiRule
  | (FOURTH | INT_4 TH?) #rOccInFouRule
  | (FIFTH  | INT_5 TH?) #rOccInFifRule
  | LAST                 #rOccinLasRule
  ;

relative_target
  : day_of_week #rTarDowRule
  | relaxed_month #rTarRelMonRule
  | relative_date_span #rTarRelDsRule
  ;

relative_time_target
  : relative_time_span #rTimTarRts
  ;

relative_time_span
  : HOUR #rtsHour
  | MINUTE #rtsMin
  | SECOND #rtsSec
  ;

implicit_prefix
  : (THIS | CURRENT) #iPrefThisRule
  ;

relative_date_prefix
  : (THIS WHITE_SPACE)? LAST #rdpLastRule
  | (THIS WHITE_SPACE)? NEXT #rdpNextRule
  | (THIS WHITE_SPACE)? PAST #rdpPastRule
  | (THIS WHITE_SPACE)? COMING #rdpComeRule
  | (THIS WHITE_SPACE)? UPCOMING #rdpUpComeRule
  | IN WHITE_SPACE (AM | AN) #rdpInMeridRule
  | (IN WHITE_SPACE)? spelled_or_int_optional_prefix #rdpSoipRule
  ;

prefix
  : relative_date_prefix #prefRdpRule
  | implicit_prefix #implPrefRdpRule
  ;

relative_date_suffix
  // from now, after today
  : (FROM | AFTER) WHITE_SPACE (NOW | TODAY) #rdsFromRule
  | AGO #rdsAgoRule
  ;

relative_time_suffix
  // from now, after today, before noon, after 4pm
  : (FROM | AFTER) (WHITE_SPACE relative_time_suffix_anchor)? #rtsFromRule

  // before noon, before 3pm
  | BEFORE (WHITE_SPACE relative_time_suffix_anchor)? #rtsBeforeRule

  | AGO #rtsAgoRule
  ;

relative_time_suffix_anchor
  : named_relative_time #rtsaNrtRule
  | explicit_time #rtsaExtRule
  ;

relative_date_span
  : DAY #rdtDayRule
  | WEEK #rtdDayWeekRule
  | MONTH #rdtDayMonthRule
  | YEAR #rdtYearRule
  ;

day_of_week
  : SUNDAY #dowSunRule
  | MONDAY #dowMonRule
  | TUESDAY #dowTueRule
  | WEDNESDAY #dowWedRule
  | THURSDAY #dowThurRule
  | FRIDAY #dowFriRule
  | SATURDAY #dowSatRule
  ;

named_relative_date
  : (TODAY | NOW) #nrdTodNowRule
  | TOMORROW #nrdTomRule
  | YESTERDAY #nrdYestRule
  ;

named_relative_time
  : NOW #nrtNowRule
  ;

// ********** holidays **********

holiday
  : spelled_or_int_optional_prefix WHITE_SPACE holiday_name WHITE_SPACE relative_date_suffix #hSpoiopRule

  | relative_date_prefix WHITE_SPACE holiday_name #hRdpRule

  | holiday_name relaxed_year_prefix relaxed_year #holNameYearRule

  | holiday_name #holNameRule
  ;

holiday_name
  : APRIL WHITE_SPACE FOOL (WHITE_SPACE DAY)?

  | BLACK WHITE_SPACE FRIDAY

  | CHRISTMAS WHITE_SPACE EVENING

  | CHRISTMAS (WHITE_SPACE DAY)?

  | COLUMBUS WHITE_SPACE DAY

  | EARTH WHITE_SPACE DAY

  | EASTER (WHITE_SPACE (SUNDAY | DAY))?

  | FATHER WHITE_SPACE DAY

  | FLAG WHITE_SPACE DAY

  | GOOD WHITE_SPACE FRIDAY

  | GROUNDHOG WHITE_SPACE? DAY

  | HALLOWEEN (WHITE_SPACE DAY)?

  | INAUGURATION WHITE_SPACE DAY

  | INDEPENDENCE WHITE_SPACE DAY

  | KWANZAA (WHITE_SPACE DAY)?

  | LABOR WHITE_SPACE DAY

  | MLK (WHITE_SPACE DAY)?

  | MEMORIAL WHITE_SPACE DAY

  | MOTHER WHITE_SPACE DAY

  | NEW WHITE_SPACE YEAR WHITE_SPACE EVENING

  | NEW WHITE_SPACE YEAR (WHITE_SPACE DAY)?

  | PATRIOT WHITE_SPACE DAY

  | PRESIDENT WHITE_SPACE DAY

  | (SAINT | ST DOT?) WHITE_SPACE PATRICK WHITE_SPACE DAY

  | TAX WHITE_SPACE DAY

  | THANKSGIVING (WHITE_SPACE DAY)?

  | ELECTION WHITE_SPACE DAY

  | VALENTINE WHITE_SPACE DAY

  | VETERAN WHITE_SPACE DAY
  ;

season
  : spelled_or_int_optional_prefix WHITE_SPACE season_name WHITE_SPACE relative_date_suffix

  | relative_date_prefix WHITE_SPACE season_name

  | season_name relaxed_year_prefix relaxed_year

  | season_name
  ;

season_name
  :WINTER
  | SPRING
  | SUMMER
  | (FALL | AUTUMN)
  ;

// ********** time rules **********

relative_time
  // 10 hours ago, 20 minutes before noon
  : spelled_or_int_optional_prefix WHITE_SPACE relative_time_target WHITE_SPACE relative_time_suffix #rtSpoipRule

  // next hour, last minute
  | prefix WHITE_SPACE relative_time_target #rtPrefRule
  ;

// a time with an hour, optional minutes, and optional meridian indicator
explicit_time
  : (AT WHITE_SPACE?)? explicit_time_hours_minutes (WHITE_SPACE? time_zone)? #xtExplicitTimeRule

  | named_time (WHITE_SPACE time_zone)? #xtNamedTimeRule
  ;

explicit_time_hours_minutes returns [String rethours, String retminutes, String ampm]
  : hours (COLON | DOT | MILITARY_HOUR_SUFFIX)? minutes ((COLON | DOT)? seconds)? (WHITE_SPACE? (meridian_indicator | (MILITARY_HOUR_SUFFIX | HOUR)))?
      {$rethours=$hours.text; $retminutes=$minutes.text; $ampm=$meridian_indicator.text;} #xtHourMinRule

  | hours (WHITE_SPACE? meridian_indicator)?
      {$rethours=$hours.text; $ampm=$meridian_indicator.text;} #xtHourRule
  ;

// hour of the day
hours
  : int_00_to_23_optional_prefix
  ;
// minutes of the hour
minutes
  : int_00_to_59_mandatory_prefix
  ;

// seconds of the minute
seconds
  : int_00_to_59_mandatory_prefix
  ;

// meridian am/pm indicator
meridian_indicator
  : simple_meridian_indicator
  | friendly_meridian_indicator
  ;

simple_meridian_indicator
  : AM
  | PM
  ;

friendly_meridian_indicator
  : (((IN WHITE_SPACE THE) | AT) WHITE_SPACE)?
    (
      MORNING
      | (NOON | EVENING | NIGHT)
    )
  ;

named_time
  : named_time_prefix? named_hour (WHITE_SPACE AT)? WHITE_SPACE hm=explicit_time_hours_minutes #namedTimeHour

    // If the named time is at night, but the hour given is before 5, we'll assume tomorrow morning
  | named_time_prefix? named_hour #namedTime
  ;

named_time_prefix
  : ((IN WHITE_SPACE THE) | AT | THIS) WHITE_SPACE #namedTimePrefix
  ;

named_hour returns [String ampm]
  : MORNING  {$ampm="am";} #MORNING
  | MIDNIGHT {$ampm="am";} #MIDNIGHT
  | NOON     {$ampm="pm";} #NOON
  | NIGHT    {$ampm="pm";} #NIGHT
  | TONIGHT  {$ampm="pm";} #TONIGHT
  | EVENING  {$ampm="pm";} #EVENING
  ;

time_zone
  : time_zone_plus_offset
  | time_zone_abbreviation
  ;

time_zone_plus_offset
  : UTC? time_zone_offset
  ;

time_zone_offset
  : (PLUS | DASH) hours (COLON? minutes)?
  ;

time_zone_abbreviation
  : UTC #UTC
  | EST #EST
  | CST #CST
  | PST #PST
  | MST #MST
  | AKST #AKST
  | HAST #HAST
  | SAST #SAST
  ;
