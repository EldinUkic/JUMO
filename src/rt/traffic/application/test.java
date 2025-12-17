package rt.traffic.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class test {

    // Standard: Logger pro Klasse
    private static final Logger log = LoggerFactory.getLogger(test.class);

    public static void main(String[] args) {

        log.info("Start");
        log.debug("x={}",2);
        log.error("Fehler", 3);
    }
}
