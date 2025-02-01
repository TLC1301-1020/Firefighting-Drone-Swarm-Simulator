/**
 * {@code Response} object created from the drone subsystem after processing a fire request
 */
public class Response {
    /**
     * original fire request that invoked drone actions
     */
    private FireRequest originalRequest;

    /**
     * status of the response indicating drone action is: complete, not complete, or error
     */
    private String status;

    /**
     * creates a response instance with the given fire request and status
     * @param originalRequest the fire request being responded to
     * @param status the status of the response
     */
    public Response(FireRequest originalRequest, String status) {
        this.originalRequest = originalRequest;
        this.status = status;
    }

    // Getters and toString()

    /**
     * get the original fire request
     * @return the original fire request
     */
    public FireRequest getOriginalRequest() {
        return originalRequest;
    }

    /**
     * get the status of the response
     * @return response status
     */
    public String getStatus() {
        return status;
    }

    /**
     * creates a string representation of the response
     * @return string representation of the response
     */
    @Override
    public String toString() {
        return String.format(
                "Response{request=%s, status=%s}",
                originalRequest.toString(), status
        );
    }
}