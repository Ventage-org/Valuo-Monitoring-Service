# 4.3 Implementación Aplicación / Solución (Código)

## Backend de Evaluación Financiera: Microservicio de Rentabilidad (`valuo-profitability-service`)

El microservicio `valuo-profitability-service` constituye el motor de inteligencia financiera y toma de decisiones del ecosistema Valuo. Desarrollado en **Java 21 con Spring Boot 3**, fue diseñado bajo los principios de **Domain-Driven Design (DDD) táctico** y el patrón arquitectural **CQRS (Command Query Responsibility Segregation)**. Su propósito es transformar las estimaciones estadísticas de tasación generadas por el modelo de Machine Learning en métricas estandarizadas de rentabilidad para la industria inmobiliaria: Tasa de Capitalización No Apalancada (*Unlevered Cap Rate*), Rendimiento Bruto (*Gross Rental Yield*), Años de Recuperación Bruta (*GRM / PER*) y *Payback* Operativo Neto, contrastándolas contra el benchmark oficial publicado por el Banco Central de Reserva del Perú (BCRP).

Arquitectónicamente, el servicio opera bajo el principio estricto de **Database-per-Service**, gestionando de forma autónoma su propio esquema relacional `profitability` en PostgreSQL sin llaves foráneas físicas hacia otros microservicios. Todas las interacciones con el catálogo de inmuebles (`msInmuebles`) y el orquestador de tasaciones (`msPrediccion`) se resuelven a nivel de aplicación mediante identificadores lógicos (UUIDs) y contratos REST síncronos de baja latencia.

La estructura general de módulos implementada en el microservicio se presenta a continuación:

```
valuo-profitability-service/
└── src/main/java/pe/ventage/valuo/profitability/
    ├── domain/
    │   ├── model/
    │   │   ├── aggregates/
    │   │   │   └── ProfitabilityCalculation.java      # Agregado Raíz, invariantes y resolución bimoneda
    │   │   ├── valueobjects/
    │   │   │   ├── ReturnMetrics.java                  # Cap Rate unlevered, Gross Yield, GRM y Payback
    │   │   │   ├── FinancialCascade.java               # Cascada operativa GSI -> OpEx -> NOI (USD y PEN)
    │   │   │   ├── OerRatio.java                       # Value Object OER (0.0000 a 0.3500, default 15%)
    │   │   │   ├── DistrictMarketBenchmark.java        # Benchmark oficial BCRP para 12 distritos de Lima
    │   │   │   └── BenchmarkCategory.java              # Semáforo de mercado con banda de tolerancia ±10%
    │   │   ├── commands/
    │   │   │   └── CalculateProfitabilityCommand.java  # Comando de intención de escritura CQRS
    │   │   └── queries/
    │   │       └── GetProfitabilityByIdQuery.java      # Consulta de lectura CQRS
    │   └── services/
    │       ├── ProfitabilityCommandService.java        # Puerto de entrada para comandos
    │       └── ProfitabilityQueryService.java          # Puerto de entrada para consultas
    ├── application/
    │   └── internal/
    │       ├── commandservices/
    │       │   └── ProfitabilityCommandServiceImpl.java# Orquestación de casos de uso e integración JIT
    │       ├── queryservices/
    │       │   └── ProfitabilityQueryServiceImpl.java  # Recuperación optimizada de solo lectura
    │       └── outboundservices/
    │           └── ExternalPredictionsService.java    # Puerto de salida hacia msPrediccion
    ├── infrastructure/
    │   ├── persistence/jpa/repositories/
    │   │   └── ProfitabilityCalculationRepository.java# Repositorio Spring Data JPA (schema profitability)
    │   └── clients/predictions/
    │       ├── PredictionsRestClientAdapter.java       # Adaptador HTTP RestClient con timeouts
    │       └── PredictionApiResponse.java             # DTO privado de infraestructura externa
    └── interfaces/
        └── rest/
            ├── ProfitabilityController.java            # Endpoints REST (/rentabilidad y /simular <5ms)
            ├── resources/                              # DTOs de transporte REST (Records)
            └── transform/                              # Assemblers desacoplados entidad <-> recurso
```

A continuación, se detalla la selección de las líneas de código fuente más representativas del núcleo de dominio financiero, explicando su lógica de diseño, formulación matemática e impacto en la solución.

---

### 1. Agregado Raíz y Resolución Bimoneda con Invariantes de Negocio (`ProfitabilityCalculation.java`)

En cumplimiento de las directrices de DDD, el agregado `ProfitabilityCalculation` es la única entidad que administra el ciclo de vida del análisis y custodia la consistencia de las reglas de negocio. En el mercado limeño, las transacciones de compraventa cotizan mayoritariamente en Dólares Americanos (USD), mientras que el canon de arrendamiento y la salida nativa del modelo predictivo (`mlService`) se expresan en Soles Nominales (PEN). 

Para evitar distorsiones de arbitraje financiero ficticio, el agregado impone la regla de **invarianza matemática del tipo de cambio**: ambas magnitudes se homologan bajo un mismo factor cambiario unificado (`DEFAULT_EXCHANGE_RATE = 3.7500` o ingresado por el usuario), persistiendo el valor en la columna `exchange_rate_used` para fines de trazabilidad y auditoría pericial en los reportes PDF.

```java
// domain/model/aggregates/ProfitabilityCalculation.java
public ProfitabilityCalculation(CalculateProfitabilityCommand command) {
    super();
    validateInvariants(command);

    this.agenteId = command.agenteId();
    this.inmuebleId = command.inmuebleId();
    this.prediccionVentaId = command.prediccionVentaId();
    this.prediccionAlquilerId = command.prediccionAlquilerId();
    this.distritoNombre = command.distritoNombre();
    this.observacionesSupuestos = command.observaciones();
    this.avisoNormativo = AVISO_NORMATIVO_LEGAL;

    // 1. Tipo de cambio unificado e inmutable (persiste en la entidad)
    BigDecimal tc = (command.tipoCambioUsado() != null && command.tipoCambioUsado().compareTo(BigDecimal.ZERO) > 0)
            ? command.tipoCambioUsado()
            : DEFAULT_EXCHANGE_RATE;
    this.tipoCambioUsado = tc.setScale(4, RoundingMode.HALF_UP);
    this.oerRatio = OerRatio.of(command.oerRatio());

    // 2. Homologación Bimoneda PEN <-> USD sin arbitraje
    if (command.valorVentaPen() != null && command.valorVentaPen().compareTo(BigDecimal.ZERO) > 0) {
        this.valorVentaPen = command.valorVentaPen().setScale(2, RoundingMode.HALF_UP);
        this.valorVentaUsd = this.valorVentaPen.divide(this.tipoCambioUsado, 2, RoundingMode.HALF_UP);
    } else {
        this.valorVentaUsd = command.valorVentaUsd().setScale(2, RoundingMode.HALF_UP);
        this.valorVentaPen = this.valorVentaUsd.multiply(this.tipoCambioUsado).setScale(2, RoundingMode.HALF_UP);
    }

    if (command.alquilerMensualPen() != null && command.alquilerMensualPen().compareTo(BigDecimal.ZERO) > 0) {
        this.alquilerMensualPen = command.alquilerMensualPen().setScale(2, RoundingMode.HALF_UP);
        this.alquilerMensualUsd = this.alquilerMensualPen.divide(this.tipoCambioUsado, 2, RoundingMode.HALF_UP);
    } else {
        this.alquilerMensualUsd = command.alquilerMensualUsd().setScale(2, RoundingMode.HALF_UP);
        this.alquilerMensualPen = this.alquilerMensualUsd.multiply(this.tipoCambioUsado).setScale(2, RoundingMode.HALF_UP);
    }

    // 3. Ejecución de la Cascada Operativa y derivación de Métricas de Retorno
    this.financialCascade = FinancialCascade.calculate(this.alquilerMensualUsd, this.oerRatio, this.tipoCambioUsado);
    this.returnMetrics = ReturnMetrics.calculate(
            this.valorVentaUsd,
            this.financialCascade.getGrossScheduledIncomeUsd(),
            this.financialCascade.getNetOperatingIncomeUsd(),
            this.distritoNombre
    );
}
```

Este componente encapsula además el aviso normativo legal obligatorio (`avisoNormativo`), cumpliendo con la exigencia académica y pericial de explicitar que los cálculos corresponden a una proyección referencial al contado (*all-cash*), sin constituir una tasación reglamentaria arancelaria.

---

### 2. Value Object de Métricas de Retorno Unlevered y Dualidad GRM/Payback (`ReturnMetrics.java`)

Este Value Object inmutable concentra la formulación matemática de los rendimientos financieros. Conforme a la literatura especializada (*Patrocínio et al., 2024* y *Manganelli et al., 2024*), el cálculo del **Cap Rate se efectúa en su forma *unlevered*** (no apalancada), aislando la rentabilidad operativa del activo de las condiciones particulares de financiamiento o endeudamiento de cada inversionista.

Asimismo, atiende una diferenciación metodológica crítica: separa los **Años de Recuperación Bruta (GRM / PER)** —utilizados para contrastar con el índice oficial del BCRP— del **Payback Operativo Neto** ($V / \text{NOI}$), el cual descuenta los costos operativos reales y ofrece una estimación transparente del tiempo de recuperación sobre el flujo de caja neto.

```java
// domain/model/valueobjects/ReturnMetrics.java
public static ReturnMetrics calculate(BigDecimal salePriceUsd, BigDecimal gsiUsd, BigDecimal noiUsd, String districtName) {
    if (salePriceUsd == null || salePriceUsd.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidFinancialInputException("El valor comercial de venta debe ser mayor a 0");
    }
    if (gsiUsd == null || gsiUsd.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidFinancialInputException("El ingreso bruto anual (GSI) debe ser mayor a 0");
    }
    if (noiUsd == null || noiUsd.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidFinancialInputException("El ingreso operativo neto (NOI) debe ser mayor a 0");
    }

    // 1. Tasa de Capitalización Neta No Apalancada: Cap Rate = (NOI / Venta) * 100
    BigDecimal capRate = noiUsd.divide(salePriceUsd, 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(4, RoundingMode.HALF_UP);

    // 2. Rendimiento Bruto Anualizado: Gross Yield = (GSI / Venta) * 100
    BigDecimal grossYield = gsiUsd.divide(salePriceUsd, 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(4, RoundingMode.HALF_UP);

    // 3. Multiplicador de Renta Bruta (PER distrital BCRP): GRM = Venta / GSI
    BigDecimal grm = salePriceUsd.divide(gsiUsd, 2, RoundingMode.HALF_UP);

    // 4. Payback Operativo Neto Real (deduciendo OpEx): Payback = Venta / NOI = 100 / Cap Rate
    BigDecimal netPayback = salePriceUsd.divide(noiUsd, 2, RoundingMode.HALF_UP);

    // 5. Clasificación algorítmica frente al Benchmark BCRP del distrito
    BenchmarkCategory category = BenchmarkCategory.classify(grossYield, districtName);

    return new ReturnMetrics(capRate, grossYield, grm, netPayback, category);
}
```

La precisión de los cálculos se fija en 4 decimales para ratios porcentuales y 2 decimales para magnitudes anuales y monetarias mediante `RoundingMode.HALF_UP`, previniendo errores de redondeo en análisis financieros acumulativos.

---

### 3. Cascada Financiera Operativa Bimoneda (`FinancialCascade.java`)

Este Value Object modela la cascada operativa estándar de la industria inmobiliaria residencial: desde el Ingreso Bruto Potencial Anualizado (*Gross Scheduled Income - GSI*), pasando por la deducción de Gastos Operativos (*Operating Expenses - OpEx*), hasta la determinación del Ingreso Operativo Neto (*Net Operating Income - NOI*). 

La cascada computa simultáneamente los flujos en USD y Soles (PEN) empleando el ratio OER (*Operating Expense Ratio*) parametrizado por el asesor (entre 0% y 35%, con línea base del 15%):

```java
// domain/model/valueobjects/FinancialCascade.java
public static FinancialCascade calculate(BigDecimal monthlyRentalUsd, OerRatio oerRatio, BigDecimal exchangeRate) {
    if (monthlyRentalUsd == null || monthlyRentalUsd.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidFinancialInputException("El canon de arrendamiento mensual debe ser mayor a 0");
    }
    if (oerRatio == null) {
        throw new InvalidFinancialInputException("El OER es requerido para calcular la cascada financiera");
    }
    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidFinancialInputException("El tipo de cambio referencial debe ser mayor a 0");
    }

    // 1. Ingreso Bruto Anual: GSI = Alquiler mensual * 12
    BigDecimal gsiUsd = monthlyRentalUsd.multiply(MONTHS_PER_YEAR).setScale(2, RoundingMode.HALF_UP);

    // 2. Gastos Operativos Anuales: OpEx = GSI * OER (vacancia, mantenimiento, corretaje)
    BigDecimal opexUsd = gsiUsd.multiply(oerRatio.getValue()).setScale(2, RoundingMode.HALF_UP);

    // 3. Ingreso Operativo Neto: NOI = GSI - OpEx = GSI * (1 - OER)
    BigDecimal noiUsd = gsiUsd.subtract(opexUsd).setScale(2, RoundingMode.HALF_UP);

    // 4. Proyección dual equivalente en Soles (PEN) a precios corrientes de mercado
    BigDecimal gsiPen = gsiUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    BigDecimal noiPen = noiUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);

    return new FinancialCascade(gsiUsd, opexUsd, noiUsd, gsiPen, noiPen);
}
```

La deducción de OpEx contempla factores críticos del mercado limeño como la tasa de vacancia estacional, mantenimiento preventivo de copropiedad y corretaje, proveyendo al inversionista la visibilidad real del flujo neto que genera el activo antes del servicio de deuda e impuestos a la renta.

---

### 4. Benchmark Distrital BCRP y Semáforo de Mercado con Tolerancia Relativa (`DistrictMarketBenchmark.java` y `BenchmarkCategory.java`)

Para dotar al asesor inmobiliario de una referencia institucional objetiva, el sistema incorpora la serie estadística trimestral oficial del **Banco Central de Reserva del Perú (BCRP)** correspondiente al indicador *Precio de Venta / Alquiler Anual (PER)* (series `PD41940PQ` a `PD41951PQ`).

Dado que el BCRP publica oficialmente esta serie para 12 distritos consolidados de Lima Metropolitana, el componente `DistrictMarketBenchmark` mantiene una estructura inmutable en memoria optimizada con soporte de normalización de cadenas y alias fonéticos (`magdalena del mar` $\rightarrow$ `magdalena`, `santiago de surco` $\rightarrow$ `surco`). Por su parte, `BenchmarkCategory` evalúa el rendimiento del inmueble aplicando una **banda de tolerancia relativa del $\pm 10\%$**:

```java
// domain/model/valueobjects/BenchmarkCategory.java
public enum BenchmarkCategory {
    BAJO("Bajo mercado", "Rendimiento bruto por debajo del promedio BCRP del distrito (más de 10% menor)."),
    PROMEDIO("En mercado", "Rendimiento bruto alineado con el promedio BCRP del distrito (±10%)."),
    ATRACTIVO("Sobre mercado", "Rendimiento bruto por encima del promedio BCRP del distrito (más de 10% mayor)."),
    SIN_REFERENCIA("Sin referencia BCRP", "El BCRP no publica el indicador PER para este distrito; no hay benchmark comparable.");

    /** Tolerancia relativa respecto al rendimiento de mercado distrital (±10%) */
    private static final BigDecimal TOLERANCE_LOW = new BigDecimal("0.90");
    private static final BigDecimal TOLERANCE_HIGH = new BigDecimal("1.10");

    public static BenchmarkCategory classify(BigDecimal grossYieldPercentage, String districtName) {
        if (grossYieldPercentage == null) {
            throw new InvalidFinancialInputException("El Gross Yield no puede ser nulo para clasificar el benchmark");
        }
        return DistrictMarketBenchmark.findByDistrict(districtName)
                .map(ref -> {
                    BigDecimal lower = ref.grossYieldPercentage().multiply(TOLERANCE_LOW);
                    BigDecimal upper = ref.grossYieldPercentage().multiply(TOLERANCE_HIGH);
                    if (grossYieldPercentage.compareTo(lower) < 0) {
                        return BAJO;
                    } else if (grossYieldPercentage.compareTo(upper) > 0) {
                        return ATRACTIVO;
                    }
                    return PROMEDIO;
                })
                .orElse(SIN_REFERENCIA);
    }
}
```

Esta lógica previene falsos positivos al evaluar inmuebles: si un departamento rinde dentro del rango del 90% al 110% del promedio de su distrito, se cataloga rigurosamente como *"En mercado"* (`PROMEDIO`). Si supera en más del 10% relativo el rendimiento distrital, se califica como *"Sobre mercado"* (`ATRACTIVO`), alertando sobre una oportunidad de inversión destacada. En distritos sin serie BCRP histórica (ej. Los Olivos o Carabayllo), el sistema retorna de manera honesta `SIN_REFERENCIA`, evitando inducir a error al usuario.
