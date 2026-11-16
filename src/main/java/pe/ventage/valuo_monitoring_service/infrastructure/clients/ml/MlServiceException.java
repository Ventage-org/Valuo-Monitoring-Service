package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml;

import lombok.Getter;

@Getter
public class MlServiceException extends RuntimeException {
    private final int status;

    public MlServiceException(int status, String message) {
        super(message);
        this.status = status;
    }
}
