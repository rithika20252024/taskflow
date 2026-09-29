package com.taskflow.api;

import com.taskflow.api.dto.*;
import com.taskflow.domain.Job;
import com.taskflow.domain.JobAttempt;
import com.taskflow.service.JobService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private final JobService jobService;

    @Autowired
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Creates a new job with idempotency guarantee.
     * Clients supply an Idempotency-Key header. Duplicate calls return the original job response.
     */
    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request) {

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank()) 
                ? idempotencyKey 
                : UUID.randomUUID().toString();

        JobResponse response = jobService.submitJob(request, effectiveKey);
        HttpStatus status = response.isCachedIdempotentResponse() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(JobResponse.fromEntity(job, false));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs() {
        List<JobResponse> jobs = jobService.getRecentJobs().stream()
                .map(j -> JobResponse.fromEntity(j, false))
                .collect(Collectors.toList());
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID jobId) {
        jobService.cancelJob(jobId);
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(JobResponse.fromEntity(job, false));
    }

    @GetMapping("/{jobId}/attempts")
    public ResponseEntity<List<JobAttemptResponse>> getJobAttempts(@PathVariable UUID jobId) {
        List<JobAttemptResponse> attempts = jobService.getJobAttempts(jobId).stream()
                .map(JobAttemptResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/attempts")
    public ResponseEntity<List<JobAttemptResponse>> listRecentAttempts() {
        List<JobAttemptResponse> attempts = jobService.getRecentAttempts().stream()
                .map(JobAttemptResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attempts);
    }

    /**
     * Concurrency test endpoint: launches N competing worker threads against the given job
     * to empirically demonstrate JPA optimistic locking and race-condition prevention.
     */
    @PostMapping("/{jobId}/simulate-race")
    public ResponseEntity<WorkerRaceResponse> simulateWorkerRace(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "20") int workerCount) {

        WorkerRaceResponse response = jobService.simulateWorkerRace(jobId, workerCount);
        return ResponseEntity.ok(response);
    }
}
