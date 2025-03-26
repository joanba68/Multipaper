package puregero.multipaper.server.velocity;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONObject;
import org.slf4j.Logger;


/**
 * Scaling
 */
public class ScalingManager {
    private final static String masterAddress = "k8s-master";
    private final static int masterPort = 8080;
    private final static String scaleEndpoint = "/scale_up";
    private final static String deleteEndpoint = "/delete_pod";

    private static final JsonConverter jsonConverter = new OrgJsonConverter();

    private final Logger logger;

    public static final class ScaleRequest {
        private final Integer num_replicas;
        private final String namespace;
        private final String deployment_name;

        private ScaleRequest(Integer num_replicas, String namespace, String deployment_name) {
            this.num_replicas = num_replicas;
            this.namespace = namespace;
            this.deployment_name = deployment_name;
        }

        private static ScaleRequest of() {
            return new ScaleRequest(null, null, null);
        }

        private static ScaleRequest of(int numReplicas) {
            return new ScaleRequest(numReplicas, null, null);
        }

        public Integer getNum_replicas() {
            return num_replicas;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getDeployment_name() {
            return deployment_name;
        }

        @Override
        public String toString() {
              return "ScaleRequest [num_replicas=" + num_replicas + ", namespace=" + namespace + ", deployment_name="
                  + deployment_name + "]";
        }
    }

    public static final class DeleteRequest {
        private final String pod_name;
        private final String namespace;
        private final Boolean update_replicas;
        private final String deployment_name;

        private DeleteRequest(String pod_name, String namespace, Boolean update_replicas, String deployment_name) {
            this.pod_name = pod_name;
            this.namespace = namespace;
            this.update_replicas = update_replicas;
            this.deployment_name = deployment_name;
        }

        private static DeleteRequest of(String podName) {
            return new DeleteRequest(podName, null, null, null);
        }

        public String getPod_name() {
            return pod_name;
        }

        public String getNamespace() {
            return namespace;
        }

        public Boolean getUpdate_replicas() {
            return update_replicas;
        }

        public String getDeployment_name() {
            return deployment_name;
        }

        @Override
        public String toString() {
              return "DeleteRequest [pod_name=" + pod_name + ", namespace=" + namespace + ", update_replicas="
                  + update_replicas + ", deployment_name=" + deployment_name + "]";
        }
    }

    public interface JsonConverter {
        String toJson(Object obj);
    }

    private static class OrgJsonConverter implements JsonConverter {
        @Override
        public String toJson(Object obj) {
            JSONObject json = new JSONObject(obj);
            return json.toString();
        }
    }

    public ScalingManager(Logger logger) {
        this.logger = logger;
    }

    public boolean scaleUp() {
        try {
            return handleResponse(post(scaleEndpoint, ScaleRequest.of()));
        } catch (IOException e) {
            logger.error("Error scaling up", e);
            return false;
        }    
    }

    public boolean scaleUp(int numReplicas) {
        try {
            return handleResponse(post(scaleEndpoint, ScaleRequest.of(numReplicas)));
        } catch (IOException e) {
            logger.error("Error scaling up", e);
            return false;
        }
    }

    public boolean deletePod(String podName) {
        logger.info("DeleteRequest {}", ScaleRequest.of(1));
        logger.info("DeleteRequest json {}", jsonConverter.toJson(ScaleRequest.of(1)));
        logger.info("ScaleRequest {}", DeleteRequest.of(podName));
        logger.info("ScaleRequest json {}", jsonConverter.toJson(DeleteRequest.of(podName)));
        try {
            return handleResponse(post(deleteEndpoint, DeleteRequest.of(podName)));
        } catch (IOException e) {
            logger.error("Error deleting pod", e);
            return false;
        }
    }

    private HttpURLConnection post(String endpoint, Object body) throws IOException {
        URL url = buildUrl(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        logger.info("Sending JSON: {}", jsonConverter.toJson(body));
        connection.getOutputStream().write(jsonConverter.toJson(body).getBytes());
        return connection;
    }

    private boolean handleResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        String response = new String(connection.getInputStream().readAllBytes());
        logger.info("Response code: {}, response: {}", responseCode, response);
        return responseCode == 200;
    }

    private static URL buildUrl(String endpoint) throws MalformedURLException {
        return new URL("http", masterAddress, masterPort, endpoint);
    }
}
