package com.polytech.knowledgebase.structure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StructureApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(StructureApplication.class, args);

		if ("none".equalsIgnoreCase(context.getEnvironment().getProperty("spring.main.web-application-type"))) {
			context.close();
		}
	}

}
