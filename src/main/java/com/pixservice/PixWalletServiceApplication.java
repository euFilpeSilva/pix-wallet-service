package com.pixservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class PixWalletServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixWalletServiceApplication.class, args);
	}

}
