package ca.yorku.eecs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;

import com.sun.net.httpserver.HttpExchange;
import static org.neo4j.driver.v1.Values.parameters;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Path;
import org.neo4j.driver.v1.types.Relationship;

public class Utils {

	private Driver driver;
	private String uriDb;
	private boolean databaseConfigured = false;

	/*
	 * Constructor for setting up the Neo4j driver
	 */
	public void setUp() {
		uriDb = "bolt://localhost:7687";
		Config config = Config.builder().withoutEncryption().build();
		driver = GraphDatabase.driver(uriDb, AuthTokens.basic("neo4j", "12345678"), config);
		databaseConfigured = true;
	}

	/*
	 * Main handler method to handle incoming HTTP requests
	 */
	public void handle(HttpExchange request) throws IOException {

		try {
			// If the Neo4j driver is not set up, set it up
			if (!databaseConfigured) {
				setUp();
			}
			// Check the request method (PUT or GET) and handle accordingly
			if (request.getRequestMethod().equals("PUT")) {
				handlePut(request);
				System.out.println("put");
			} else if (request.getRequestMethod().equals("GET")) {
				handleGet(request);
				System.out.println("get");
			} else
				sendString(request, "Unimplemented method\n", 501);
		} catch (Exception e) {
			e.printStackTrace();
			sendString(request, "Server error\n", 500);
		}

	}

	/*
	 * This method maps PUT requests to their respective handler
	 */
	private void handlePut(HttpExchange request) throws IOException {

		// Extract endpoint from the request URI
		String uri = request.getRequestURI().toString();

		// Check which endpoint is being accessed and call the corresponding method
		if (uri.contains("/api/v1/addActor")) {
			addActor(request);
		}

		else if (uri.contains("/api/v1/addMovie")) {
			addMovie(request);
		}

		else if (uri.contains("/api/v1/addRelationship")) {
			addRelationship(request);
		}
		
		else if (uri.contains("/api/v1/addNationality")) {
			addNationality(request);
		}

		else {
			sendString(request, "Bad Request\n", 400);
		}
	}

	/*
	 * This method maps GET requests to their respective handler
	 */
	private void handleGet(HttpExchange request) throws IOException {

		// Extract the exact endpoint from the request URI
		String uri = request.getRequestURI().toString();

		// Check which endpoint is being accessed and call the corresponding method
		if (uri.contains("/api/v1/getActor")) {
			getActor(request);
		}

		else if (uri.contains("/api/v1/getMovie")) {
			getMovie(request);
		}

		else if (uri.contains("/api/v1/hasRelationship")) {
			hasRelationship(request);
		}
		
		else if (uri.contains("/api/v1/getNationality")) {
			getNationality(request);
		}

		else if (uri.contains("/api/v1/computeBaconNumber")) {
			computeBaconNumber(request);
		}

		else if (uri.contains("/api/v1/computeBaconPath")) {
			computeBaconPath(request);
		}

		else {
			sendString(request, "Bad Request\n", 400);
		}
	}

	/*
	 * This method adds an actor node into the database
	 */
	public void addActor(HttpExchange request) throws IOException {

        String name;
        String actorId;
        String nationality;
        
        int status = 400; // If a name or actorId is missing send a status code of 400 - BAD REQUEST

        try {
            String body = convert(request.getRequestBody());
            JSONObject deserialized = new JSONObject(body);

            // Check JSONObject to confirm that parameters match that of `addActor`
            if (deserialized.has("name") && deserialized.has("actorId") && !deserialized.has("nationality")) {
                name = deserialized.getString("name");
                actorId = deserialized.getString("actorId");
                status = insertActor(name, actorId); // Attempt to add actor node to DB
            }
            else if (deserialized.has("nationality") && deserialized.has("name") && deserialized.has("actorId")) {
                nationality = deserialized.getString("nationality");
                name = deserialized.getString("name");
                actorId = deserialized.getString("actorId");
                status = insertActorNationality(name, actorId, nationality);
            }
            
        }

        // Deserialization failed
        catch (JSONException e) {
            status = 400;
        }

        // Server or database connection failed, internal server error
        catch (Exception e) {
            status = 500;
        }

        sendString(request, "", status);
    }

	public int insertActor(String name, String actorId) {

		if (actorExists(actorId)) {
			return 400;
		}

		try (Session session = driver.session()) {
			session.writeTransaction(
					tx -> tx.run("CREATE (a:actor {name:$x, id:$y})", parameters("x", name, "y", actorId)));
			session.close();
			return 200; // Actor successfully added
		} catch (Exception e) {
			return 500; // Actor insertion failed
		}
	}
	
	public int insertActorNationality(String name, String actorId, String nationality) {

        if (actorExists(actorId)) {
            return 400;
        }

        try (Session session = driver.session()) {
            session.writeTransaction(tx -> tx.run("CREATE (a:actor {name:$x, id:$y, nationality: $n})", parameters("x", name, "y", actorId, "n", nationality)));
            session.close();
            return 200; // Actor successfully added
        } catch (Exception e) {
            return 500; // Actor insertion failed
        }
    }

	/*
	 * This method returns: True - if an actor with the given id is already present
	 * in the database False - if an actor with the given id is not present in the
	 * database
	 */
	public boolean actorExists(String id) {
		boolean isPresent = false;

		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				StatementResult result = tx.run("MATCH (a:actor) WHERE a.id = $actorId RETURN a",
						parameters("actorId", id));
				// isPresent = result.list().size() > 0;
				isPresent = result.hasNext();
				System.out.println("THe value of isPresent in actorPresent method: " + isPresent);
			}
		}

		return isPresent;
	}

	/*
	 * This method adds a movie node into the database
	 */
	public void addMovie(HttpExchange request) throws IOException {

		String name;
		String movieId;
		int status = 400; // If a name or an movieId is missing send a status code of 400 - BAD REQUEST

		try {
			String body = convert(request.getRequestBody());
			JSONObject deserialized = new JSONObject(body);

			// Check JSONObject to see if parameters match that of `addMovie`
			if (deserialized.has("name") && deserialized.has("movieId")) {
				name = deserialized.getString("name");
				movieId = deserialized.getString("movieId");
				status = insertMovie(name, movieId); // Attempt to add movie node to DB
			}
		}

		// Deserialization failed
		catch (JSONException e) {
			status = 400;
		}

		// Server or database connection failed, internal server error
		catch (Exception e) {
			e.printStackTrace();
			status = 500;
		}

		sendString(request, "", status);
	}

	public int insertMovie(String name, String movieId) {

		if (movieExists(movieId)) {
			return 400;
		}

		try (Session session = driver.session()) {
			session.writeTransaction(
					tx -> tx.run("CREATE (m:movie {name:$x, id:$y})", parameters("x", name, "y", movieId)));
			session.close();
			return 200; // Actor successfully added
		} catch (Exception e) {
			e.printStackTrace();
			return 500; // Actor insertion failed
		}
	}
	
	public void addNationality(HttpExchange request) throws IOException {

		String actorId;
		String nationality;
		int status = 400; // If a name or an movieId is missing send a status code of 400 - BAD REQUEST

		try {
			String body = convert(request.getRequestBody());
			JSONObject deserialized = new JSONObject(body);

			// Check JSONObject to see if parameters match that of `addNationality`
			if (deserialized.has("actorId") && deserialized.has("nationality")) {
				actorId = deserialized.getString("actorId");
				nationality = deserialized.getString("nationality");
				status = insertNationality(actorId, nationality); // Attempt to add movie node to DB
			}
			
			else {
				System.out.println("not found");
			}
		}

		// Deserialization failed
		catch (JSONException e) {
			status = 400;
		}

		// Server or database connection failed, internal server error
		catch (Exception e) {
			
			status = 500;
		}

		sendString(request, "", status);
	}

	public int insertNationality(String actorId, String nationality) {

		if (!actorExists(actorId)) {
			System.out.println("error here");
			return 404;
		}
		
		else if (nationalityExists(nationality)) {
			return 400;
		}
		
		else {
			try (Session session = driver.session()) {
				session.writeTransaction(
						tx -> tx.run("MATCH (a:actor {id: $x}) SET a.nationality = $y", parameters("x", actorId, "y", nationality)));
				session.close();
				return 200; // Actor successfully added
			} catch (Exception e) {
				e.printStackTrace();
				return 500; // Actor insertion failed
			}
		}
	}
	
	public boolean nationalityExists(String id) {
		boolean isPresent = false;

		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				StatementResult result = tx.run("MATCH (a:actor) WHERE a.nationality = $nationality RETURN a",
						parameters("nationality", id)); 
				isPresent = result.hasNext();
				System.out.println("Nationality is present: " + isPresent);
			}
		}

		return isPresent;
	}

	
	

	/*
	 * This method returns: True - if a movie with the given id is already present
	 * in the database False - if a movie with the given id is not present in the
	 * database
	 */
	public boolean movieExists(String id) {
		boolean isPresent = false;

		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				StatementResult result = tx.run("MATCH (m:movie) WHERE m.id = $id RETURN m", parameters("id", id));
				isPresent = result.hasNext();
			}
		}

		return isPresent;
	}

	/*
	 * Method to insert a relationship (ACTED_IN) between an actor and a movie in
	 * the database
	 */

	public void addRelationship(HttpExchange request) throws IOException {

		String actorId;
		String movieId;
		int status = 400;

		try {
			String body = convert(request.getRequestBody());
			JSONObject deserialized = new JSONObject(body);

			// Check JSONObject to see if parameters match that of `addRelationship`
			if (deserialized.has("actorId") && deserialized.has("movieId")) {
				actorId = deserialized.getString("actorId");
				movieId = deserialized.getString("movieId");
				status = insertRelationship(actorId, movieId);
			}
		}

		// Deserialization failed
		catch (JSONException e) {
			status = 400;
		}

		// Server or database connection failed, internal server error
		catch (Exception e) {
			e.printStackTrace();
			status = 500;
		}

		sendString(request, "", status);
	}
	

	public int insertRelationship(String actorId, String movieId) {

		if (!actorExists(actorId) || !movieExists(movieId)) {
			return 404;
		}

		if (relationshipExists(actorId, movieId)) {
			return 400;
		}

		try (Session session = driver.session()) {
			session.writeTransaction(tx -> tx.run(
					"MATCH (a:actor), (m:movie)\n" + "WHERE a.id = $x AND m.id = $y\n"
							+ "CREATE (a)-[r:ACTED_IN]->(m)\n" + "RETURN type(r)",
					parameters("x", actorId, "y", movieId))); // do we need to have this return statement??
			session.close();
			return 200; // Actor successfully added
		} catch (Exception e) {
			e.printStackTrace();
			return 400; // Actor insertion failed
		}
	}

	/*
	 * This method returns: True - if a relationship (ACTED_IN) exists between an
	 * actor and a movie False - if a relationship (ACTED_IN) does not exist between
	 * an actor and a movie
	 */
	public boolean relationshipExists(String actorId, String movieId) {
		boolean isPresent = false;

		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				StatementResult node_boolean = tx.run(
						"RETURN EXISTS ((:actor {id: $actorId})-[:ACTED_IN*1]-(:movie {id: $movieId})) AS bool",
						parameters("actorId", actorId, "movieId", movieId));
				isPresent = node_boolean.single().get("bool").asBoolean();
			}
		}

		return isPresent;
	}

	/*
	 * Method to get the name and ID of the actor, and the list of actors acting in that actor
	 */
	private void getActor(HttpExchange request) throws IOException { // should access modifier be public or private

		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		Map<String, String> queryParam = splitQuery(query);
		String actorId = queryParam.get("actorId");
		int statusCode = 400;
		
		System.out.println("value of actorId: "+ actorId);

		
		if (actorId == null || actorId.trim().isEmpty()) {
		    statusCode = 400;
		}
		
		
		else if (!actorExists(actorId)) {
			statusCode = 404;
		}

		else {
			try (Session session = driver.session()) { // check edge case ??
				try (Transaction tx = session.beginTransaction()) {

					StatementResult nameResult = tx.run("MATCH (a:actor {id: $id}) RETURN a.name", parameters("id", actorId));
					
					String actorName = "";
					
					if (nameResult.hasNext()) {
						actorName = nameResult.next().get(0).asString();
					} 
					
					StatementResult moviesResult = tx.run(
							"MATCH (a:actor {id: $x})-[:ACTED_IN]->(movie)\n RETURN movie.id",
							parameters("x", actorId));
					
					List<String> moviesArray = new ArrayList<>();
					while (moviesResult.hasNext()) {
						String movie = moviesResult.next().get(0).asString();
		                moviesArray.add(movie);
					}
					
					StatementResult NationalityResult = tx.run("MATCH (a:actor {id: $id}) RETURN a.nationality", parameters("id", actorId));
					
					String actorNationality = "";
					
					if (NationalityResult.hasNext()) {
						actorNationality = NationalityResult.next().get(0).asString();
					} 

					JSONObject jsonObj = new JSONObject();
					jsonObj.put("actorId", actorId);
					jsonObj.put("name", actorName);
					jsonObj.put("movies", new JSONArray(moviesArray));
					if (actorNationality != null && !actorNationality.trim().isEmpty()) {
						jsonObj.put("nationality", actorNationality);
					}
					sendString(request, jsonObj.toString(), 200);
					return;

				}

				catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
					statusCode = 400;
				}

				catch (Exception e) { // if server connection / database connection failed, internal server error
					e.printStackTrace();
					statusCode = 500;
				}
			}
		}

		sendString(request, "", statusCode);

	}
	
	/*
	 * Method to get the name and ID of the movie, and the list of actors acting in that movie
	 */
	public void getMovie(HttpExchange request) throws IOException {
		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		Map<String, String> queryParam = splitQuery(query);
		String movieId = queryParam.get("movieId");
		int statusCode = 400;

		
		if (movieId == null || movieId.trim().isEmpty()) {
		    statusCode = 400;
		}
		
		else if (!movieExists(movieId)) {
			statusCode = 404;
		}

		else {
			try (Session session = driver.session()) { // check edge case ??
				try (Transaction tx = session.beginTransaction()) {
					StatementResult nameResult = tx.run("MATCH (m: movie {id: $id}) RETURN m.name",
							parameters("id", movieId));
					
					String movieName = "";
					
					if (nameResult.hasNext()) {
						movieName = nameResult.next().get(0).asString();
					}

					StatementResult actorsResult = tx.run(
							"MATCH (m:movie {id: $x})<-[:ACTED_IN]-(actor)\n RETURN actor.id",
							parameters("x", movieId));
					
					List<String> actorsArray = new ArrayList<>();

					while (actorsResult.hasNext()) {
						String actor = actorsResult.next().get(0).asString();
						actorsArray.add(actor);
					}

					JSONObject jsonObj = new JSONObject();
					jsonObj.put("movieId", movieId);
					jsonObj.put("name", movieName);
					jsonObj.put("actors", new JSONArray(actorsArray));
					sendString(request, jsonObj.toString(), 200);
					return;

				}

				catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
					statusCode = 400;
				}

				catch (Exception e) { // if server connection / database connection failed, internal server error
					statusCode = 500;
				}
			}
		}

		sendString(request, "", statusCode);
	}

	/*
	 * Method to check if a relationship (ACTED_IN) exists between an actor and a
	 * movie
	 */
	public void hasRelationship(HttpExchange request) throws IOException {
		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		Map<String, String> queryParam = splitQuery(query);
		String actorId = queryParam.get("actorId");
		String movieId = queryParam.get("movieId");
		boolean hasRelationship = false;
		int statusCode = 400;

		
		if (movieId == null || movieId.trim().isEmpty() || actorId == null || actorId.trim().isEmpty()) {
		    statusCode = 400;
		}

		
		else if (!actorExists(actorId) || !movieExists(movieId)) {
			statusCode = 404;
		}

		else {
			try (Session session = driver.session()) { // check edge case ??
				try (Transaction tx = session.beginTransaction()) {
					StatementResult node_boolean = tx.run(
							"RETURN EXISTS ((:actor {id: $actorId})-[:ACTED_IN*1]-(:movie {id: $movieId})) AS bool",
							parameters("actorId", actorId, "movieId", movieId));
					
					hasRelationship = node_boolean.single().get("bool").asBoolean();

					JSONObject jsonObj = new JSONObject();
					jsonObj.put("actorId", actorId);
					jsonObj.put("movieId", movieId);
					jsonObj.put("hasRelationship", hasRelationship);
					sendString(request, jsonObj.toString(), 200);
					return;

				}

				catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
					statusCode = 400;
				}

				catch (Exception e) { // if server connection / database connection failed, internal server error
					statusCode = 500;
				}
			}
		}

		sendString(request, "", statusCode);
	}
	
	
	/*
	 * Method to get the nationality of the actor
	 */
	public void getNationality(HttpExchange request) throws IOException {

		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		Map<String, String> queryParam = splitQuery(query);
		String nationality = queryParam.get("nationality");
		System.out.println(nationality);
		int statusCode = 400;
		
		 
		if (nationality == null || nationality.trim().isEmpty()) {
		    statusCode = 400;
		}
		
		else if (!nationalityExists(nationality)) {
			statusCode = 404;
		}
		  

		else {
			try (Session session = driver.session()) { // check edge case ??
				try (Transaction tx = session.beginTransaction()) {
 
					StatementResult actorsResult = tx.run(
							"MATCH (a:actor {nationality: $x}) RETURN a.name",
							parameters("x", nationality));
					
					List<String> actorsArray = new ArrayList<>();

					while (actorsResult.hasNext()) {
						String actor = actorsResult.next().get(0).asString();
						actorsArray.add(actor);
					}

					JSONObject jsonObj = new JSONObject();
					 
					jsonObj.put("actors", new JSONArray(actorsArray));
					sendString(request, jsonObj.toString(), 200);
					return;

				}

				catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
					e.printStackTrace();
					statusCode = 400;
				}

				catch (Exception e) { // if server connection / database connection failed, internal server error
					e.printStackTrace();
					statusCode = 500;
				}
			}
		}
		sendString(request, "", statusCode);
	}
	
	
	

	/*
	 * Method to compute the Bacon number of an actor
	 */
	public void computeBaconNumber(HttpExchange request) throws IOException {
		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		System.out.println(query);
		Map<String, String> queryParam = splitQuery(query);
		System.out.println(queryParam);
		String actorId = queryParam.get("actorId");
		int baconNumber = 0;
		String baconID = "nm0000102";

		int statusCode = 400;
		
		if (actorId == null || actorId.trim().isEmpty()) {
			System.out.println("error here");
		    statusCode = 400;
		}

		else if (!actorExists(actorId)) {
			statusCode = 404;
		}

		else if (actorId.equals("nm0000102")) {
			baconNumber = 0;
			 JSONObject jsonObj = new JSONObject();
             try {
				jsonObj.put("baconNumber", baconNumber);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
             sendString(request, jsonObj.toString(), 200);
             return;
		}

		else {
			try (Session session = driver.session()) { // check edge case ??
                try (Transaction tx = session.beginTransaction()) {
                    StatementResult baconCount = tx.run(
                            "MATCH path = shortestPath((a:actor {id: $actor1})-[:ACTED_IN*]-(b:actor {id: $actor2})) RETURN length(path)/2 as baconPathNumber",
                            parameters("actor1", actorId, "actor2", baconID));
 
                    
                    if (baconCount.hasNext()) {
                        baconNumber = baconCount.next().get("baconPathNumber").asInt();
                    }  
  
                    JSONObject jsonObj = new JSONObject();
                    jsonObj.put("baconNumber", baconNumber);
                    sendString(request, jsonObj.toString(), 200);
                    return;

                }

                catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
                    statusCode = 400;
                }

                catch (Exception e) { // if server connection / database connection failed, internal server error
                	e.printStackTrace();
                    statusCode = 500;
                }
            }
		}

		sendString(request, "", statusCode);

	}

	/*
	 * Method to compute the Bacon path of an actor
	 */
	public void computeBaconPath(HttpExchange request) throws IOException {

		URI uri = request.getRequestURI();
		String query = uri.getQuery();
		System.out.println(query);
		Map<String, String> queryParam = splitQuery(query);
		System.out.println(queryParam);
		String actorId = queryParam.get("actorId");
 		String baconID = "nm0000102";
		int statusCode = 400; 

		List<String> baconIDArray = new ArrayList<String>(); 

		if (actorId == null || actorId.trim().isEmpty()) {
		    statusCode = 400;
		}

		else if (!actorExists(actorId)) {
			statusCode = 404;
		}

		else if (actorId.equals("nm0000102")) {
			baconIDArray.add(baconID);
			JSONObject jsonObj = new JSONObject();

			try {
				jsonObj.put("baconPath", new JSONArray(baconIDArray));
			} catch (JSONException e) {
 				e.printStackTrace();
			}
			sendString(request, jsonObj.toString(), 200);
			return;

		}

		else {
			try (Session session = driver.session()) { // check edge case ??
				try (Transaction tx = session.beginTransaction()) {
					try {
						StatementResult result = tx.run(
								"MATCH path = shortestPath((a:actor {id: $actor1})-[:ACTED_IN*]-(b:actor {id: $actor2})) RETURN nodes(path) AS nodes",
								parameters("actor1", actorId, "actor2", baconID));
						 
						List<Node> nodes = new ArrayList<>();
						
						if (result.hasNext()) {
		                    Record record = result.next();
		                    nodes = record.get("nodes").asList(node -> node.asNode());
		                }  
						
						if (nodes.size() == 0) {
							statusCode = 404;
 							sendString(request, "", 404);
							return;
						}
						
						else {
							for (int i = 0; i < nodes.size(); i++) { 
			                     Node node = nodes.get(i);  
	 		                         baconIDArray.add(node.get("id").asString());
			                }

							 
							JSONObject jsonObj = new JSONObject();
							jsonObj.put("baconPath", new JSONArray(baconIDArray));
							sendString(request, jsonObj.toString(), 200);
							return;
							
						}
					}

					catch (NullPointerException e) {
						statusCode = 404;
					}

				}

				// no path found

				catch (JSONException e) { // if deserialized failed, (ex: JSONObject Null Value)
					statusCode = 400;
				}

				catch (Exception e) { // if server connection / database connection failed, internal server error
					e.printStackTrace();
					statusCode = 500;
				}
			}
		}

		sendString(request, "", statusCode);

	}

	/*
	 * Method to send a response string back to the client
	 */
	private void sendString(HttpExchange request, String data, int restCode) throws IOException {
		request.sendResponseHeaders(restCode, data.length());
		OutputStream os = request.getResponseBody();
		os.write(data.getBytes());
		os.close();
	}

	
	// use for extracting query params
	public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
					URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}

	// one possible option for extracting JSON body as String
	public static String convert(InputStream inputStream) throws IOException {

		try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
			return br.lines().collect(Collectors.joining(System.lineSeparator()));
		}
	}

	// another option for extracting JSON body as String
	public static String getBody(HttpExchange he) throws IOException {
		InputStreamReader isr = new InputStreamReader(he.getRequestBody(), "utf-8");
		BufferedReader br = new BufferedReader(isr);

		int b;
		StringBuilder buf = new StringBuilder();
		while ((b = br.read()) != -1) {
			buf.append((char) b);
		}

		br.close();
		isr.close();

		return buf.toString();
	}
}