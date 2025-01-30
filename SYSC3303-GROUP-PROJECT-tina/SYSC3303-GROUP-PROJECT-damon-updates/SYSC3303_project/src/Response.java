public class Response {
    private FireRequest originalRequest;
    private String status;

    public Response(FireRequest originalRequest, String status) {
        this.originalRequest = originalRequest;
        this.status = status;
    }

    // Getters and toString()
    public FireRequest getOriginalRequest() {
        return originalRequest;
    }
    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format(
                "Response{request=%s, status=%s}",
                originalRequest.toString(), status
        );
    }
}