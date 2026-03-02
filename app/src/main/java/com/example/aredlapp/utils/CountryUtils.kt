package com.example.aredlapp.utils

object CountryUtils {
    //ok i'm ass at country codes so i just copied one over the internet to auto replace country codes
    private val countryMap = mapOf(
        "004" to "AFG", "008" to "ALB", "010" to "ATA", "012" to "DZA", "016" to "ASM", "020" to "AND", "024" to "AGO", "028" to "ATG",
        "031" to "AZE", "032" to "ARG", "036" to "AUS", "040" to "AUT", "044" to "BHS", "048" to "BHR", "050" to "BGD", "051" to "ARM",
        "052" to "BRB", "056" to "BEL", "060" to "BMU", "064" to "BTN", "068" to "BOL", "070" to "BIH", "072" to "BWA", "074" to "BVT",
        "076" to "BRA", "084" to "BLZ", "086" to "IOT", "090" to "SLB", "092" to "VGB", "096" to "BRN", "100" to "BGR", "104" to "MMR",
        "108" to "BDI", "112" to "BLR", "116" to "KHM", "120" to "CMR", "124" to "CAN", "132" to "CPV", "136" to "CYM", "140" to "CAF",
        "144" to "LKA", "148" to "TCD", "152" to "CHL", "156" to "CHN", "158" to "TWN", "162" to "CHR", "166" to "CCK", "170" to "COL",
        "174" to "COM", "175" to "MYT", "178" to "COG", "180" to "COD", "184" to "COK", "188" to "CRI", "191" to "HRV", "192" to "CUB",
        "196" to "CYP", "203" to "CZE", "204" to "BEN", "208" to "DNK", "212" to "DMA", "214" to "DOM", "218" to "ECU", "222" to "SLV",
        "226" to "GNQ", "231" to "ETH", "232" to "ERI", "233" to "EST", "234" to "FRO", "238" to "FLK", "239" to "SGS", "242" to "FJI",
        "246" to "FIN", "248" to "ALA", "250" to "FRA", "254" to "GUF", "258" to "PYF", "260" to "ATF", "262" to "DJI", "266" to "GAB",
        "268" to "GMB", "270" to "GMB", "275" to "PSE", "276" to "DEU", "288" to "GHA", "292" to "GIB", "296" to "KIR", "300" to "GRC",
        "304" to "GRL", "308" to "GRD", "312" to "GLP", "316" to "GUM", "320" to "GTM", "324" to "GIN", "328" to "GUY", "332" to "HTI",
        "334" to "HMD", "336" to "VAT", "340" to "HND", "344" to "HKG", "348" to "HUN", "352" to "ISL", "356" to "IND", "360" to "IDN",
        "364" to "IRN", "368" to "IRQ", "372" to "IRL", "376" to "ISR", "380" to "ITA", "384" to "CIV", "388" to "JAM", "392" to "JPN",
        "398" to "KAZ", "400" to "JOR", "404" to "KEN", "408" to "PRK", "410" to "KOR", "414" to "KWT", "417" to "KGZ", "418" to "LAO",
        "422" to "LBN", "426" to "LSO", "428" to "LVA", "430" to "LBR", "434" to "LBY", "438" to "LIE", "440" to "LTU", "442" to "LUX",
        "446" to "MAC", "450" to "MDG", "454" to "MWI", "458" to "MYS", "462" to "MDV", "466" to "MLI", "470" to "MLT", "474" to "MTQ",
        "478" to "MRT", "480" to "MUS", "484" to "MEX", "492" to "MCO", "496" to "MNG", "498" to "MDA", "499" to "MNE", "500" to "MSR",
        "504" to "MAR", "508" to "MOZ", "512" to "OMN", "516" to "NAM", "520" to "NRU", "524" to "NPL", "528" to "NLD", "531" to "CUW",
        "533" to "ABW", "534" to "SXM", "535" to "BES", "540" to "NCL", "548" to "VUT", "554" to "NZL", "558" to "NIC", "562" to "NER",
        "566" to "NGA", "570" to "NIU", "574" to "NFK", "578" to "NOR", "580" to "MNP", "581" to "UMI", "583" to "FSM", "584" to "MHL",
        "585" to "PLW", "586" to "PAK", "591" to "PAN", "598" to "PNG", "600" to "PRY", "604" to "PER", "608" to "PHL", "612" to "PCN",
        "616" to "POL", "620" to "PRT", "624" to "GNB", "626" to "TLS", "630" to "PRI", "634" to "QAT", "638" to "REU", "642" to "ROU",
        "643" to "RUS", "646" to "RWA", "652" to "BLM", "654" to "SHN", "659" to "KNA", "660" to "AIA", "662" to "LCA", "663" to "MAF",
        "666" to "SPM", "670" to "VCT", "674" to "SMR", "678" to "STP", "682" to "SAU", "686" to "SEN", "688" to "SRB", "690" to "SYC",
        "694" to "SLE", "702" to "SGP", "703" to "SVK", "704" to "VNM", "705" to "SVN", "706" to "SOM", "710" to "ZAF", "716" to "ZWE",
        "724" to "ESP", "728" to "SSD", "729" to "SDN", "732" to "ESH", "740" to "SUR", "744" to "SJM", "748" to "SWZ", "752" to "SWE",
        "756" to "CHE", "760" to "SYR", "762" to "TJK", "764" to "THA", "768" to "TGO", "772" to "TKL", "776" to "TON", "780" to "TTO",
        "784" to "ARE", "788" to "TUN", "792" to "TUR", "795" to "TKM", "796" to "TCA", "798" to "TUV", "800" to "UGA", "804" to "UKR",
        "807" to "MKD", "818" to "EGY", "826" to "GBR", "831" to "GUE", "832" to "JEY", "833" to "IMN", "834" to "TZA", "840" to "USA",
        "850" to "VIR", "854" to "BFA", "858" to "URY", "860" to "UZB", "862" to "VEN", "876" to "WLF", "882" to "WSM", "887" to "YEM",
        "894" to "ZMB"
    )

    fun getCountryName(code: String?): String {
        if (code == null) return "-"
        val formattedCode = code.padStart(3, '0')
        return countryMap[formattedCode] ?: code
    }
}
