parser grammar NumericRules;
tokens {
  INT
}
  
// ********** numeric rules **********

// a number between 00 and 59 inclusive, with a mandatory 0 prefix before numbers 0-9
int_00_to_59_mandatory_prefix
  : (INT_00
  | int_01_to_12
  | int_13_to_23
  | int_24_to_31
  | int_32_to_59)
  ;
  
// a number between 00 and 99 inclusive, with a mandatory 0 prefix before numbers 0-9
int_00_to_99_mandatory_prefix
  : (int_00_to_59_mandatory_prefix | int_60_to_99)
  ;
  
// a number between 1 and 12 inclusive, with an optional 0 prefix before numbers 1-9
int_01_to_12_optional_prefix
  : (int_1_to_9 | int_01_to_12)
  ;
  
// a number between 0 and 23 inclusive, with an optional 0 prefix before numbers 0-9
int_00_to_23_optional_prefix
  : (INT_00 
  | INT_0
  | int_1_to_9
  | int_01_to_12
  | int_13_to_23)
  ;
  
// a number between 1 and 31 inclusive, with an optional 0 prefix before numbers 1-9
int_01_to_31_optional_prefix
  : (int_01_to_12
  | int_1_to_9
  | int_13_to_23
  | int_24_to_31)
  ;
  
// a number with exactly four digits
int_four_digits
  : int_00_to_99_mandatory_prefix int_00_to_99_mandatory_prefix
  ;
  
// a number between one and thirty-one either spelled-out, or as an
// integer with an optional 0 prefix for numbers betwen 1 and 9
spelled_or_int_01_to_31_optional_prefix
  : int_01_to_31_optional_prefix
  | spelled_one_to_thirty_one
  ;
  
// a number between 1 and 9999 either spelled-out, or as an
// integer with an optional 0 prefix for numbers betwen 1 and 9
spelled_or_int_optional_prefix
  : spelled_one_to_thirty_one // TODO expand this spelled range to at least ninety-nine
  | ((int_01_to_31_optional_prefix | int_32_to_59 | int_60_to_99)
     ( INT_0 | INT_00 | int_01_to_31_optional_prefix | int_32_to_59 | int_60_to_99))?
  ;
  
// a spelled number between one and thirty-one (one, two, etc.)
spelled_one_to_thirty_one
  : ONE
  | TWO
  | THREE
  | FOUR
  | FIVE
  | SIX
  | SEVEN
  | EIGHT
  | NINE
  | TEN
  | ELEVEN
  | TWELVE
  | THIRTEEN
  | FOURTEEN
  | FIFTEEN
  | SIXTEEN
  | SEVENTEEN
  | EIGHTEEN
  | NINETEEN
  | TWENTY WHITE_SPACE ONE
  | TWENTY DASH? ONE
  | TWENTY WHITE_SPACE TWO
  | TWENTY DASH? TWO
  | TWENTY WHITE_SPACE THREE
  | TWENTY DASH? THREE
  | TWENTY WHITE_SPACE FOUR
  | TWENTY DASH? FOUR
  | TWENTY WHITE_SPACE FIVE
  | TWENTY DASH? FIVE
  | TWENTY WHITE_SPACE SIX
  | TWENTY DASH? SIX
  | TWENTY WHITE_SPACE SEVEN
  | TWENTY DASH? SEVEN
  | TWENTY WHITE_SPACE EIGHT
  | TWENTY DASH? EIGHT
  | TWENTY WHITE_SPACE NINE
  | TWENTY DASH? NINE
  | TWENTY
  | THIRTY WHITE_SPACE ONE
  | THIRTY DASH? ONE
  | THIRTY
  ;
  
// a spelled number in sequence between first and thirty-first
spelled_first_to_thirty_first
  : (FIRST       | INT_1 ST)
  | (SECOND      | INT_2 ND)
  | (THIRD       | INT_3 RD)
  | (FOURTH      | INT_4 TH)
  | (FIFTH       | INT_5 TH)
  | (SIXTH       | INT_6 TH)
  | (SEVENTH     | INT_7 TH)
  | (EIGHTH      | INT_8 TH)
  | (NINTH       | INT_9 TH)
  | (TENTH       | INT_10 TH)
  | (ELEVENTH    | INT_11 TH)
  | (TWELFTH     | INT_12 TH)
  | (THIRTEENTH  | INT_13 TH)
  | (FOURTEENTH  | INT_14 TH)
  | (FIFTEENTH   | INT_15 TH)
  | (SIXTEENTH   | INT_16 TH)
  | (SEVENTEENTH | INT_17 TH)
  | (EIGHTEENTH  | INT_18 TH)
  | (NINETEENTH  | INT_19 TH)
  | (TWENTIETH   | INT_20 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? FIRST)   | INT_21 ST)
  | ((TWENTY (DASH | WHITE_SPACE)? SECOND)  | INT_22 ND)
  | ((TWENTY (DASH | WHITE_SPACE)? THIRD)   | INT_23 RD)
  | ((TWENTY (DASH | WHITE_SPACE)? FOURTH)  | INT_24 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? FIFTH)   | INT_25 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? SIXTH)   | INT_26 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? SEVENTH) | INT_27 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? EIGHTH)  | INT_28 TH)
  | ((TWENTY (DASH | WHITE_SPACE)? NINTH)   | INT_29 TH)
  | (THIRTIETH | INT_30 TH)
  | ((THIRTY (DASH | WHITE_SPACE)? FIRST)   | INT_31 ST)
  ;
  
int_60_to_99
  : INT_60 | INT_61 | INT_62 | INT_63 | INT_64 | INT_65 | INT_66 | INT_67 | INT_68
  | INT_69 | INT_70 | INT_71 | INT_72 | INT_73 | INT_74 | INT_75 | INT_76 | INT_77
  | INT_78 | INT_79 | INT_80 | INT_81 | INT_82 | INT_83 | INT_84 | INT_85 | INT_86
  | INT_87 | INT_88 | INT_89 | INT_90 | INT_91 | INT_92 | INT_93 | INT_94 | INT_95
  | INT_96 | INT_97 | INT_98 | INT_99
  ;
  
int_32_to_59
  : INT_32 | INT_33 | INT_34 | INT_35 | INT_36 | INT_37 | INT_38 | INT_39 | INT_40 
  | INT_41 | INT_42 | INT_43 | INT_44 | INT_45 | INT_46 | INT_47 | INT_48 | INT_49 
  | INT_50 | INT_51 | INT_52 | INT_53 | INT_54 | INT_55 | INT_56 | INT_57 | INT_58 
  | INT_59
  ;
   
int_24_to_31
  : INT_24 | INT_25 | INT_26 | INT_27 | INT_28 | INT_29  | INT_30 | INT_31
  ;
   
int_13_to_23
  : INT_13 | INT_14 | INT_15 | INT_16 | INT_17 | INT_18 | INT_19  | INT_20 | INT_21
  | INT_22 | INT_23
  ;
   
int_01_to_12
  : INT_01 | INT_02 | INT_03 | INT_04 | INT_05 | INT_06 | INT_07 | INT_08 | INT_09
  | INT_10 | INT_11 | INT_12
  ;
  
int_1_to_9
  : INT_1  | INT_2  | INT_3  | INT_4  | INT_5  | INT_6  | INT_7  | INT_8  | INT_9
  ;
  
int_1_to_5
  : INT_1  | INT_2  | INT_3  | INT_4  | INT_5 
  ;
  