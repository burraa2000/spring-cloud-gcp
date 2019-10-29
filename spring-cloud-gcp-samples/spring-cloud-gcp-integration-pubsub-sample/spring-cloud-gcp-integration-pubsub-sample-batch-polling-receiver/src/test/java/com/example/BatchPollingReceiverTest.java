/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.output.TeeOutputStream;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

/**
 * Tests for the receiver application.
 *
 * @author Lauren Huang
 *
 * @since 1.2
 */

@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
public class BatchPollingReceiverTest {
	private static PrintStream systemOut;

	private static ByteArrayOutputStream baos;

	@Autowired
	private PubSubTemplate pubSubTemplate;

	@BeforeClass
	public static void prepare() {
		assumeThat(
				"PUB/SUB-sample integration tests are disabled. Please use '-Dit.pubsub=true' "
						+ "to enable them. ",
				System.getProperty("it.pubsub"), is("true"));

		systemOut = System.out;
		baos = new ByteArrayOutputStream();
		TeeOutputStream out = new TeeOutputStream(systemOut, baos);
		System.setOut(new PrintStream(out));
	}

	@AfterClass
	public static void bringBack() {
		System.setOut(systemOut);
	}

	@Test
	public void testSample() throws Exception {
		String expectedString = "final message";
		int maxBatchSize = 5;
		Pattern numMessagesPattern = Pattern.compile("Batch of (\\d+) message\\(s\\) arrived by Synchronous Pull!");

		for (int i = 0; i < 100; i++) {
			this.pubSubTemplate.publish("exampleTopic", "test message " + UUID.randomUUID());
		}
		this.pubSubTemplate.publish("exampleTopic", expectedString);

		Awaitility.await()
				.atMost(60, TimeUnit.SECONDS)
				.until(() -> baos.toString().contains(expectedString));
		Matcher numMessagesMatcher = numMessagesPattern.matcher(baos.toString());
		while (numMessagesMatcher.find()) {
			assertThat(Integer.parseInt(numMessagesMatcher.group(1)) <= maxBatchSize);
		}
	}
}