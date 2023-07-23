package com.ndmsystems.coala.exceptions

enum class PayloadError(val code: Int) {
    //https://docs.google.com/document/d/1i59Hlrv3HIglOnsb87h6bhq6wU5iugC1FyqR7TbUBlM/edit#heading=h.cy3fc1y8lykr
    UNKNOWN(0), CODE_1000(1000), CODE_2000(2000), CODE_2001(2001), CODE_2002(2002), CODE_2003(2003), CODE_2004(2004), CODE_2005(2005), CODE_2006(2006), CODE_2007(
        2007
    ),
    CODE_2008(2008), CODE_2009(2009), CODE_2010(2010), CODE_2011(2011), CODE_2012(2012), CODE_2013(2013), CODE_2014(2014), CODE_2015(2015), CODE_2016(
        2016
    ),
    CODE_2017(2017), CODE_2018(2018), CODE_2019(2019), CODE_2020(2020), CODE_2021(2021), CODE_2022(2022), CODE_2023(2023), CODE_2030(2030), CODE_3000(
        3000
    );

    companion object {
        fun getByCode(code: Int?): PayloadError {
            return if (code == null) UNKNOWN else when (code) {
                1000 -> CODE_1000
                2000 -> CODE_2000
                2001 -> CODE_2001
                2002 -> CODE_2002
                2003 -> CODE_2003
                2004 -> CODE_2004
                2005 -> CODE_2005
                2006 -> CODE_2006
                2007 -> CODE_2007
                2008 -> CODE_2008
                2009 -> CODE_2009
                2010 -> CODE_2010
                2011 -> CODE_2011
                2012 -> CODE_2012
                2013 -> CODE_2013
                2014 -> CODE_2014
                2015 -> CODE_2015
                2016 -> CODE_2016
                2017 -> CODE_2017
                2018 -> CODE_2018
                2019 -> CODE_2019
                2020 -> CODE_2020
                2021 -> CODE_2021
                2022 -> CODE_2022
                2023 -> CODE_2023
                2030 -> CODE_2030
                3000 -> CODE_3000
                else -> UNKNOWN
            }
        }
    }
}