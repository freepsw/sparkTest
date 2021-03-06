package com.skcc.bds.drools.test;


import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Message.Level;
import org.kie.api.runtime.KieSession;

public class CalendarTest {

    @Test
    public void test() {
        // BZ-1007385
        String drl = "package org.drools.compiler.integrationtests;\n" +
                     "\n" +
                     "global java.util.List list\n" +
                     " \n" +
                     "rule \"weekend\"\n" +
                     "    calendars \"weekend\"\n" +
                     "    \n" +
                     "    when\n" +
                     "    then\n" +
                     "        list.add(\"weekend\");\n" +
                     "        System.out.println('this is weekend');\n" +
                     "end\n" +
                     " \n" +
                     "rule \"weekday\"\n" +
                     "    calendars \"weekday\"\n" +
                     "\n" +
                     "    when\n" +
                     "    then\n" +
                     "       list.add(\"weekday\");\n" +
                     "        System.out.println('this is weekday');\n" +
                     "end";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem();

        kfs.write("src/main/resources/kbase1/calendars.drl", drl);

        KieBuilder kbuilder = ks.newKieBuilder(kfs);

        kbuilder.buildAll();

        List<Message> res = kbuilder.getResults().getMessages(Level.ERROR);

        assertEquals(res.toString(), 0, res.size());

        KieBase kbase = ks.newKieContainer(kbuilder.getKieModule().getReleaseId()).getKieBase();

        KieSession ksession = ks.newKieContainer(kbuilder.getKieModule().getReleaseId()).newKieSession();

        ArrayList<String> list = new ArrayList<String>();

        ksession.getCalendars().set("weekend", WEEKEND);
        ksession.getCalendars().set("weekday", WEEKDAY);
        ksession.setGlobal("list", list);

        ksession.fireAllRules();
        

        System.out.println("size : " + list.size());
        
        for (String str : list)
        	System.out.println(str);
        
        ksession.dispose();

        assertEquals(1, list.size());
    }

    private static final org.kie.api.time.Calendar WEEKEND = new org.kie.api.time.Calendar() {

        @Override
        public boolean isTimeIncluded(long timestamp) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(timestamp);

            int day = c.get(Calendar.DAY_OF_WEEK);

            return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
        }
    };

    private static final org.kie.api.time.Calendar WEEKDAY = new org.kie.api.time.Calendar() {

        @Override
        public boolean isTimeIncluded(long timestamp) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(timestamp);

            int day = c.get(Calendar.DAY_OF_WEEK);
            return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
        }
    };

}
