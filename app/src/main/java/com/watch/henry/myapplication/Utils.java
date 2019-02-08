package com.watch.henry.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {


    private static String START_DATE = "19/08/2016";
    private static String BIRTH_DATE = "09/01/2019";

    public static String getDays() {
        return getDays(START_DATE) + "/" + getDays(BIRTH_DATE);
    }

    private static int getDays(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        Date startDate = null;
        Date currDate = null;
        try {
            startDate = dateFormat.parse(date);
            currDate = dateFormat.parse(dateFormat.format(new Date()));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (startDate != null && currDate != null) {
            long diff = currDate.getTime() - startDate.getTime();
            float dayCount = (float) diff / (24 * 60 * 60 * 1000);
            return (int) dayCount + 1;
        }

        return 0;
    }

}
