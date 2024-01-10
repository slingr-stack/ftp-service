package io.slingr.service.ftp;

import io.slingr.services.utils.tests.ServiceTests;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("SpellCheckingInspection")
public class FtpTest {

    private static final Logger logger = LoggerFactory.getLogger(FtpTest.class);

    private static ServiceTests test;

    @BeforeClass
    public static void init() throws Exception {
        test = ServiceTests.start(new io.slingr.service.ftp.Runner(), "test.properties");
    }

    @Test
    public void testUploadFile() {
        logger.info("-- INIT --");

        logger.info("-- END --");
    }
}