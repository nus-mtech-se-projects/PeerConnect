package mtech.swe5006.peerconnect;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PeerconnectApplication {

	public static void main(String[] args) {
		if (isPresent("APPLICATIONINSIGHTS_CONNECTION_STRING")) {
			ApplicationInsights.attach();
		}
		SpringApplication.run(PeerconnectApplication.class, args);
	}

	private static boolean isPresent(String envVar) {
		String value = System.getenv(envVar);
		return value != null && !value.isBlank();
	}

}
