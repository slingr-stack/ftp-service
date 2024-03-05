package io.slingr.service.ftp;

import io.slingr.services.utils.tests.ServiceTests;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpTest {

    private static final Logger logger = LoggerFactory.getLogger(FtpTest.class);

    @BeforeClass
    public static void init() throws Exception {
        ServiceTests test = ServiceTests.start(new Runner(), "test.properties");
    }

    @Test
    public void testUploadFile() {
        logger.info("-- INIT --");

        logger.info("-- END --");
    }
}