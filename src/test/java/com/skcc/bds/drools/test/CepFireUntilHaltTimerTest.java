package com.skcc.bds.drools.test;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.drools.core.time.SessionPseudoClock;
import org.junit.After;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Ignore;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.conf.ClockTypeOption;

/**
 * Tests proper timer firing using accumulate and fireUntilHalt() mode.
 * BZ-981270
 */

public class CepFireUntilHaltTimerTest {

    private KieSession ksession;
    private List<Long> result;
    private SessionPseudoClock clock;

    @Before
    public void init() {
        String drl = "package org.drools.compiler.test1\n" +
                     "\n" +
                     "import org.drools.compiler.test1.CepFireUntilHaltTimerTest.MetadataEvent;\n" +
                     "import java.util.List;\n" +
                     "\n" +
                     "global List countResult;\n" +
                     "\n" +
                     "declare MetadataEvent\n" +
                     "    @role( event )\n" +
                     "    @timestamp( metadataTimestamp )\n" +
                     "    @duration( metadataDuration )\n" +
                     "    @expires (24h)\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Number of metadata events in the last 10 seconds\"\n" +
                     "timer (int: 1s 10s)\n" +
                     "//timer (int: 0s 10s) // this works\n" +
                     "when\n" +
                   //  "    String( this == \"events_inserted\" )\n" +
                     "    $count: Number() from accumulate( $event: MetadataEvent() over window:time(10s),  count( $event ) )\n" +
                     "then\n" +
                     "    System.out.println(\"Events count: \" + $count);\n" +
                     "    countResult.add($count);\n" +
                     "end\n";

        KieServices ks = KieServices.Factory.get();

        KieModuleModel module = ks.newKieModuleModel();

        KieBaseModel defaultBase = module.newKieBaseModel("defaultKBase")
                                         .setDefault(true)
                                         .addPackage("*")
                                         .setEventProcessingMode(EventProcessingOption.STREAM);
        defaultBase.newKieSessionModel("defaultKSession")
                   .setDefault(true)
                   .setClockType(ClockTypeOption.get("pseudo"));

        KieFileSystem kfs = ks.newKieFileSystem()
                              .write("src/main/resources/r1.drl", drl);
        kfs.writeKModuleXML(module.toXML());
        ks.newKieBuilder(kfs).buildAll();

        ksession = ks.newKieContainer(ks.getRepository().getDefaultReleaseId())
                     .newKieSession();

        result = new ArrayList<Long>();
        ksession.setGlobal("countResult", result);

        clock = ksession.getSessionClock();
        ksession.insert(clock);
    }

    @After
    public void cleanup() {
        ksession.dispose();
    }

    @Test
    public void testTimerAccumulateFireUntilHalt() throws Exception {

        ExecutorService thread = Executors.newSingleThreadExecutor();
        final Future fireUntilHaltResult = thread.submit(new Runnable() {
            @Override
            public void run() {
                ksession.fireUntilHalt();
            }
        });


        try {

            final Date eventTime = new Date(clock.getCurrentTime());
            for (int i = 0; i < 205; i++) {
                ksession.insert(new MetadataEvent(eventTime, 0L));
            }

            // this triggers the rule on after all events had been inserted
            ksession.insert("events_inserted");

            // give time to fireUntilHalt to process the insertions
            Thread.sleep(1000);

            clock.advanceTime(1, TimeUnit.SECONDS);
           // Thread.sleep(1000);

            assertFalse("The result is unexpectedly empty", result.isEmpty());
            assertEquals(205, (long) result.get(0));
            assertEquals(0, (long) result.get(1));
        } finally {
            ksession.halt();
            // wait for the engine to finish and throw exception if any was thrown 
            // in engine's thread
            fireUntilHaltResult.get(60000, TimeUnit.SECONDS);
            thread.shutdown();
        }
    }

    public static class MetadataEvent implements Serializable {

        private static final long serialVersionUID = 6827172457832354239L;
        private Date metadataTimestamp;
        private Long metadataDuration;
        private String name;

        public MetadataEvent() {
        }

        public MetadataEvent(Date timestamp, Long duration) {
            metadataTimestamp = timestamp;
            metadataDuration = duration;
        }

        public MetadataEvent(String name, Date timestamp, Long duration) {
            this.name = name;
            metadataTimestamp = timestamp;
            metadataDuration = duration;
        }

        public Date getMetadataTimestamp() {
            return metadataTimestamp != null ? (Date) metadataTimestamp.clone() : null;
        }

        public void setMetadataTimestamp(Date metadataTimestamp) {
            this.metadataTimestamp = metadataTimestamp != null
                                     ? (Date) metadataTimestamp.clone() : null;
        }

        public Long getMetadataDuration() {
            return metadataDuration;
        }

        public void setMetadataDuration(Long metadataDuration) {
            this.metadataDuration = metadataDuration;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("MetadataEvent[name='%s' timestamp='%s', duration='%s']", name, metadataTimestamp, metadataDuration);
        }
    }

}
