package coding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class Main {

	static public class Train {

		private OffsetDateTime departureTime;
		private String destination;
		private String routeId;

		public Train(OffsetDateTime departureTime, String destination, String routeId) {
			super();
			this.departureTime = departureTime;
			this.destination = destination;
			this.routeId = routeId;
		}

		public OffsetDateTime getDepartureTime() {
			return departureTime;
		}

		public String getDestination() {
			return destination;
		}

		public String getRouteId() {
			return routeId;
		}

	}

	static String predictionEndpoint = "https://api-v3.mbta.com/predictions/?filter[stop]=place-pktrm&sort=departure_time&include=route";
	static String tripEndpoint = "https://api-v3.mbta.com/trips/";

	static OffsetDateTime now;
	private static int totalResults = 10;

	public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public static void main(String[] args) throws JsonProcessingException, IOException, ParseException {

		JsonNode baseNode = makeRequest(predictionEndpoint);

		JsonNode trainDataNode;
		if (baseNode == null) {
			return;
		}
		if (baseNode.get("data") == null)
			return;

		trainDataNode = baseNode.get("data");

		List<Train> trains = new ArrayList<Train>();
		now = OffsetDateTime.now(ZoneId.of("GMT-4"));

		if (trainDataNode.isArray()) {
			int count = 0;
			for (final JsonNode objNode : trainDataNode) {
				if (count < totalResults) {
					try {
						String routeId = objNode.get("relationships").get("route").get("data").get("id").asText();
						OffsetDateTime departureTime = OffsetDateTime
								.parse(objNode.get("attributes").get("departure_time").asText());
						String tripId = objNode.get("relationships").get("trip").get("data").get("id").asText();

						if (now.isBefore(departureTime)) {
							JsonNode tripNode = makeRequest(tripEndpoint + tripId);
							String destination = tripNode.get("data").get("attributes").get("headsign").asText();
							trains.add(new Train(departureTime, destination, routeId));
							count++;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}

				} else {
					break;
				}
			}
		} else {
			new RuntimeException();
		}

		Collections.sort(trains, (t1, t2) -> t1.departureTime.compareTo(t2.departureTime));

		printResults(trains);

	}

	private static void printResults(List<Train> trains) {
		System.out.println("Current Time" + now.format(formatter));
		HashMap<String, List<Train>> map = new HashMap<>();

		for (Train t : trains) {
			map.putIfAbsent(t.getRouteId(), new ArrayList<>());
			map.get(t.getRouteId()).add(t);
		}

		int index = 1;

		for (String route : map.keySet()) {
			List<Train> trainForRoute = map.get(route);

			System.out.println("Route======" + route);
			for (Train t : trainForRoute) {
				System.out.println("Index:" + index++ + "=>" + t.getDestination() + "   leaving in   "
						+ getDepartingTime(t.getDepartureTime()) + "   minutes");
			}
		}
	}

	private static long getDepartingTime(OffsetDateTime departureTime) {
		return (departureTime.toEpochSecond() - now.toEpochSecond()) / 60;
	}

	private static JsonNode makeRequest(String endPoint) {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endPoint))
				.method("GET", HttpRequest.BodyPublishers.noBody()).build();
		HttpResponse<String> response = null;
		// System.out.println(endPoint);
		try {
			response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode jsonNode = objectMapper.readTree(response.body());
			return jsonNode;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}
}