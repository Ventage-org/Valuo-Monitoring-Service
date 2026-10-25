package pe.ventage.valuo_monitoring_service.domain.services;

import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.SyncJobStatusCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;

public interface GovernanceCommandService {
    ModelTrainingJob handle(TriggerTrainingCommand command);
    ModelTransitionLog handle(PromoteModelCommand command);
    ModelTransitionLog handle(RollbackModelCommand command);
    ModelTrainingJob handle(SyncJobStatusCommand command);
    java.util.Map<String, Object> updateIpcConfig(Double valor, String periodo, String fuente, String actualizadoPor, String nota, String aplicarA);
}
