package com.ndmsystems.coala.exceptions

enum class PayloadError(val code: Int) {
    //https://docs.google.com/document/d/1i59Hlrv3HIglOnsb87h6bhq6wU5iugC1FyqR7TbUBlM/edit#heading=h.cy3fc1y8lykr
    UNKNOWN(code = 0),
    CODE_1000(code = 1000),
    CODE_2000(code = 2000),
    CODE_2001(code = 2001),
    CODE_2002(code = 2002),
    CODE_2003(code = 2003),
    CODE_2004(code = 2004),
    CODE_2005(code = 2005),
    CODE_2006(code = 2006),
    CODE_2007(code = 2007),
    CODE_2008(code = 2008),
    CODE_2009(code = 2009),
    CODE_2010(code = 2010),
    CODE_2011(code = 2011),
    CODE_2012(code = 2012),
    CODE_2013(code = 2013),
    CODE_2014(code = 2014),
    CODE_2015(code = 2015),
    CODE_2016(code = 2016),
    CODE_2017(code = 2017),
    CODE_2018(code = 2018),
    CODE_2019(code = 2019),
    CODE_2020(code = 2020),
    CODE_2021(code = 2021),
    CODE_2022(code = 2022),
    CODE_2023(code = 2023),
    CODE_2030(code = 2030),
    CODE_2032(code = 2032),
    CODE_3000(code = 3000),
    // weak password
    CODE_3001(code = 3001);

    companion object {
        fun getByCode(code: Int?): PayloadError = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}