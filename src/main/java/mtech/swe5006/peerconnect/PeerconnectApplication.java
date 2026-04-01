package mtech.swe5006.peerconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PeerconnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(PeerconnectApplication.class, args);
	}

}
