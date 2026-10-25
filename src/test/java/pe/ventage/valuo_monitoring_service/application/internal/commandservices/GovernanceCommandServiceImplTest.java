package pe.ventage.valuo_monitoring_service.application.internal.commandservices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService.*;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.SyncJobStatusCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.TrainingConflictException;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.ModelStage;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTrainingJobRepository;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTransitionLogRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceCommandServiceImplTest {

    @Mock
    private ExternalMlService externalMlService;

    @Mock
    private ModelTrainingJobRepository jobRepository;

    @Mock
    private ModelTransitionLogRepository transitionLogRepository;

    @InjectMocks
    private GovernanceCommandServiceImpl commandService;

    @Test
    @DisplayName("Disparar reentrenamiento de Alquiler exitosamente")
    void triggerTraining_alquiler_success() {
        TriggerTrainingCommand command = new TriggerTrainingCommand(
                OperationType.ALQUILER, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.035
        );

        when(jobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(externalMlService.isTrainingAvailable()).thenReturn(true);
        when(externalMlService.triggerTraining(any())).thenReturn(new MlJobTriggerResponse("job-alq-1", "en_progreso", "Job iniciado"));
        when(jobRepository.save(any(ModelTrainingJob.class))).thenAnswer(i -> i.getArgument(0));

        ModelTrainingJob job = commandService.handle(command);

        assertNotNull(job);
        assertEquals("job-alq-1", job.getJobId());
        assertEquals(OperationType.ALQUILER, job.getOperationType());
        verify(transitionLogRepository, times(1)).save(any(ModelTransitionLog.class));
    }

    @Test
    @DisplayName("Bloqueo de concurrencia: lanza TrainingConflictException si hay job local activo")
    void triggerTraining_localJobActive_throwsConflict() {
        TriggerTrainingCommand command = new TriggerTrainingCommand(
                OperationType.VENTA, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.04
        );

        ModelTrainingJob activeJob = new ModelTrainingJob(command, "active-job-1");
        when(jobRepository.findByStatusIn(any())).thenReturn(List.of(activeJob));

        assertThrows(TrainingConflictException.class, () -> commandService.handle(command));
        verify(externalMlService, never()).triggerTraining(any());
    }

    @Test
    @DisplayName("Bloqueo de concurrencia: lanza TrainingConflictException si ML service está ocupado")
    void triggerTraining_remoteBusy_throwsConflict() {
        TriggerTrainingCommand command = new TriggerTrainingCommand(
                OperationType.VENTA, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.04
        );

        when(jobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(externalMlService.isTrainingAvailable()).thenReturn(false);

        assertThrows(TrainingConflictException.class, () -> commandService.handle(command));
        verify(externalMlService, never()).triggerTraining(any());
    }

    @Test
    @DisplayName("Sincronización de Job: transición a SUCCESS registra evento en auditoría con métricas")
    void syncJobStatus_transitionToSuccess_recordsAuditLog() {
        TriggerTrainingCommand cmd = new TriggerTrainingCommand(OperationType.ALQUILER, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.035);
        ModelTrainingJob job = new ModelTrainingJob(cmd, "job-sync-1");

        when(jobRepository.findByJobId("job-sync-1")).thenReturn(Optional.of(job));
        when(externalMlService.getJobStatus("job-sync-1")).thenReturn(Optional.of(
                new MlJobStatusResponse("job-sync-1", "SUCCESS", "xgboost_alquiler_v3", 0.77, 410.0, 10.9, null)
        ));
        when(externalMlService.getActiveModel("alquiler")).thenReturn(Optional.of(
                new MlActiveModelInfo("xgboost_alquiler_v2", "Production", "alquiler")
        ));
        when(jobRepository.save(any(ModelTrainingJob.class))).thenAnswer(i -> i.getArgument(0));

        ModelTrainingJob updated = commandService.handle(new SyncJobStatusCommand("job-sync-1"));

        assertEquals(JobStatus.SUCCESS, updated.getStatus());
        assertEquals("xgboost_alquiler_v3", updated.getModelVersion());
        verify(transitionLogRepository, times(1)).save(any(ModelTransitionLog.class));
    }

    @Test
    @DisplayName("Sincronización de Job: transición a FAILED registra evento FAILED en auditoría")
    void syncJobStatus_transitionToFailed_recordsFailedAuditLog() {
        TriggerTrainingCommand cmd = new TriggerTrainingCommand(OperationType.VENTA, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.04);
        ModelTrainingJob job = new ModelTrainingJob(cmd, "job-fail-1");

        when(jobRepository.findByJobId("job-fail-1")).thenReturn(Optional.of(job));
        when(externalMlService.getJobStatus("job-fail-1")).thenReturn(Optional.of(
                new MlJobStatusResponse("job-fail-1", "FAILED", null, null, null, null, "Error en dataset")
        ));
        when(jobRepository.save(any(ModelTrainingJob.class))).thenAnswer(i -> i.getArgument(0));

        ModelTrainingJob updated = commandService.handle(new SyncJobStatusCommand("job-fail-1"));

        assertEquals(JobStatus.FAILED, updated.getStatus());
        assertEquals("Error en dataset", updated.getErrorMessage());
        verify(transitionLogRepository, times(1)).save(any(ModelTransitionLog.class));
    }

    @Test
    @DisplayName("Promoción de Alquiler registra deltas en auditoría")
    void promoteModel_alquiler_recordsDeltas() {
        PromoteModelCommand command = new PromoteModelCommand(
                OperationType.ALQUILER, "admin@valuo.pe", "Aprobado por comité"
        );

        when(externalMlService.promoteModel(any())).thenReturn(new MlPromoteResponse(
                true, "3", "2", "Production", "Promovido", -0.92, 0.0020, -30.0, 4.5, true
        ));
        when(transitionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ModelTransitionLog log = commandService.handle(command);

        assertNotNull(log);
        assertEquals(OperationType.ALQUILER, log.getOperationType());
        assertEquals("3", log.getModelVersion());
        assertEquals("2", log.getPreviousVersion());
        assertEquals(-0.92, log.getDeltaMapePct());
        assertTrue(log.getMejoraMape());
    }

    @Test
    @DisplayName("Rollback de Alquiler restaura versión previa y audita deltas")
    void rollbackModel_alquiler_success() {
        RollbackModelCommand command = new RollbackModelCommand(
                OperationType.ALQUILER, "2", "admin@valuo.pe", "Rollback preventivo"
        );

        when(externalMlService.getActiveModel("alquiler")).thenReturn(Optional.of(
                new MlActiveModelInfo("3", "Production", "alquiler")
        ));
        when(externalMlService.rollbackModel(any())).thenReturn(new MlRollbackResponse(
                true, "2", "3", "Rollback exitoso", 0.92, -0.0020, 30.0, -4.5, false
        ));
        when(transitionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ModelTransitionLog log = commandService.handle(command);

        assertNotNull(log);
        assertEquals(OperationType.ALQUILER, log.getOperationType());
        assertEquals("2", log.getModelVersion());
        assertEquals("3", log.getPreviousVersion());
        assertEquals("SUCCESS", log.getStatus());
    }

    @Test
    @DisplayName("Actualización de factor IPC delega en servicio ML")
    void updateIpcConfig_delegatesToExternalService() {
        when(externalMlService.updateIpcConfig(169.5, "2026-Q2", "INEI", "Joaquin Cortez", "Ajuste", "ambos"))
                .thenReturn(Map.of("exito", true));

        Map<String, Object> res = commandService.updateIpcConfig(
                169.5, "2026-Q2", "INEI", "Joaquin Cortez", "Ajuste", "ambos"
        );

        assertTrue((Boolean) res.get("exito"));
        verify(externalMlService, times(1)).updateIpcConfig(169.5, "2026-Q2", "INEI", "Joaquin Cortez", "Ajuste", "ambos");
    }
}
