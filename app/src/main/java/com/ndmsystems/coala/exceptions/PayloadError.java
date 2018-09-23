package com.ndmsystems.coala.exceptions;

public enum PayloadError {
    //https://docs.google.com/document/d/1i59Hlrv3HIglOnsb87h6bhq6wU5iugC1FyqR7TbUBlM/edit#heading=h.cy3fc1y8lykr

    UNKNOWN(0),
    CODE_1000(1000),
    CODE_2000(2000),
    CODE_2001(2001),
    CODE_2002(2002),
    CODE_2003(2003),
    CODE_2004(2004),
    CODE_2005(2005),
    CODE_2006(2006),
    CODE_2007(2007),
    CODE_2008(2008),
    CODE_2009(2009),
    CODE_2010(2010),
    CODE_2011(2011),
    CODE_2012(2012),
    CODE_2013(2013),
    CODE_2014(2014),
    CODE_2015(2015),
    CODE_2016(2016),
    CODE_2017(2017),
    CODE_2018(2018),
    CODE_2019(2019),
    CODE_2020(2020),
    CODE_2021(2021),
    CODE_2022(2022),
    CODE_2023(2023),
    CODE_3000(3000);

    private int code;

    PayloadError(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PayloadError getByCode(Integer code){
        if(code == null) return UNKNOWN;
        switch (code){
            default:
                return UNKNOWN;
            case 1000:
                return CODE_1000;
            case 2000:
                return CODE_2000;
            case 2001:
                return CODE_2001;
            case 2002:
                return CODE_2002;
            case 2003:
                return CODE_2003;
            case 2004:
                return CODE_2004;
            case 2005:
                return CODE_2005;
            case 2006:
                return CODE_2006;
            case 2007:
                return CODE_2007;
            case 2008:
                return CODE_2008;
            case 2009:
                return CODE_2009;
            case 2010:
                return CODE_2010;
            case 2011:
                return CODE_2011;
            case 2012:
                return CODE_2012;
            case 2013:
                return CODE_2013;
            case 2014:
                return CODE_2014;
            case 2015:
                return CODE_2015;
            case 2016:
                return CODE_2016;
            case 2017:
                return CODE_2017;
            case 2018:
                return CODE_2018;
            case 2019:
                return CODE_2019;
            case 2020:
                return CODE_2020;
            case 2021:
                return CODE_2021;
            case 2022:
                return CODE_2022;
            case 2023:
                return CODE_2023;
            case 3000:
                return CODE_3000;
        }
    }
}
