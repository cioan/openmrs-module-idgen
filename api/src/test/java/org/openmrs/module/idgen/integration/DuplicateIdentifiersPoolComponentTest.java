package org.openmrs.module.idgen.integration;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.IdentifierPool;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Tests out the synchronization problem where duplicate identifiers are assigned
 */
public class DuplicateIdentifiersPoolComponentTest extends BaseModuleContextSensitiveTest {

    public static final int NUM_THREADS = 25;

    @Autowired
    private IdentifierSourceService service;

    @Autowired
    @Qualifier("patientService")
    private PatientService patientService;

    @Before
    public void setUp() throws Exception {

        executeDataSet("org/openmrs/module/idgen/include/TestData.xml");

        IdentifierPool identifierPool = (IdentifierPool) Context.getService(IdentifierSourceService.class).getIdentifierSource(4);

        List<String> identifiers = new ArrayList<String>();

        for (int i = 1; i <= NUM_THREADS; ++i) {
            identifiers.add(new String("" + i));
        }

        service.addIdentifiersToPool(identifierPool, identifiers);
        service.saveIdentifierSource(identifierPool);
        Context.flushSession();
    }

    @Test
    public void testUnderLoad() throws Exception {

        final List<String> generated = new ArrayList<String>();

        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < NUM_THREADS; ++i) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Context.openSession();
                    try {
                        authenticate();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            // pass
                        }
                        generated.addAll(service.generateIdentifiers(Context.getService(IdentifierSourceService.class).getIdentifierSource(4), 1, "thread"));
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            // pass
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        Context.closeSession();
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // pass
            }
        }

        assertThat(generated.size(), is(NUM_THREADS));
        assertThat(new HashSet<String>(generated).size(), is(NUM_THREADS));
    }

}

