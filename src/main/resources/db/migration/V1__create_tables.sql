-- Tabela para armazenar o resumo de cada varredura -- 
CREATE TABLE scan_reports ( 
    id BIGSERIAL, 
    scan_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_idle_resources INT NOT NULL DEFAULT 0, 
    total_monthly_waste NUMERIC(10, 2) NOT NULL DEFAULT 0.00, 
    ai_recommendation TEXT
    PRIMARY KEY (id) 
); 

-- Armazenar recursos ociosos encontrados na AWS -- 
CREATE TABLE idle_resources( 
    id BIGSERIAL, 
    resource_id VARCHAR(100) NOT NULL, 
    resource_type VARCHAR(50) NOT NULL, 
    region VARCHAR(50) NOT NULL, 
    status VARCHAR(50) NOT NULL,  
    monthly_cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00, 
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_id BIGINT, 
    PRIMARY KEY (id), 
    CONSTRAINT fk_report FOREIGN KEY (report_id) REFERENCES scan_reports(id) ON DELETE CASCADE 
);

-- Index para otimizar consultas -- 
CREATE INDEX idx_idle_resources_type ON idle_resources(resource_type);  
CREATE INDEX idx_scan_reports_date ON scan_reports(scan_date);  
