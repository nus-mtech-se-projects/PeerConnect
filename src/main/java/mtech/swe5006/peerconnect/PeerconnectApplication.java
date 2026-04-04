package mtech.swe5006.peerconnect;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PeerconnectApplication {

	private static final Logger log = LoggerFactory.getLogger(PeerconnectApplication.class);

	public static void main(String[] args) {
		if (isPresent("APPLICATIONINSIGHTS_CONNECTION_STRING")) {
			ApplicationInsights.attach();
			log.info("Azure Monitor telemetry enabled via APPLICATIONINSIGHTS_CONNECTION_STRING.");
		} else {
			log.info(
				"Azure Monitor telemetry is disabled for this run because APPLICATIONINSIGHTS_CONNECTION_STRING is not set. "
					+ "To enable it locally, set that environment variable in the same terminal before starting the app. "
					+ "For deployed environments, add it under Azure App Service > Environment variables."
			);
		}
		SpringApplication.run(PeerconnectApplication.class, args);
	}

	private static boolean isPresent(String envVar) {
		String value = System.getenv(envVar);
		return value != null && !value.isBlank();
	}

}
