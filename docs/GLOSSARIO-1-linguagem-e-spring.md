# Dicionário de Tecnologias do Nexulor — Parte 1

> **O que é este documento:** um catálogo de tudo que o projeto usa, com quatro respostas para cada item: o que é, o que significa a sigla, para que serve e como eu usei no Nexulor.
>
> **Como ler:** a Parte 1 cobre linguagem, build e o ecossistema Spring. A Parte 2 cobre dados, mensageria e protocolos. A Parte 3 cobre resiliência, observabilidade, testes, infraestrutura e integração contínua.

---

## Linguagem e build

### Java 21 (LTS)
**O que é:** A linguagem e a plataforma de execução padrão da Oracle, na versão 21, que é uma versão de suporte longo prazo.
**Para que serve:** é a base de todo o sistema: os quatro serviços são aplicações Java.
**No Nexulor:** usei Java 21 em vez de 17 por causa das virtual threads, que só existem a partir do 19 e ficaram estáveis no 21. O `pom.xml` fixa `<java.version>21</java.version>` e o CI usa a distribuição Temurin do JDK 21.

### Virtual threads
**O que é:** Threads leves, gerenciadas pela máquina virtual em vez do sistema operacional, introduzidas no Java 21. Você continua escrevendo código de bloqueio comum, com `Thread` e `synchronized`, mas o custo de criar uma thread deixa de ser relevante.
**Para que serve:** resolver o modelo clássico do Java, em que uma thread cara por requisição limita a escala, sem obrigar o programador a reescrever tudo em estilo reativo.
**No Nexulor:** a chamada gRPC ao serviço de fraude roda dentro de uma virtual thread. Isso me deu escalabilidade sem reatividade, mas trouxe um problema de observabilidade que precisei resolver: a virtual thread não herda o contexto de rastreamento da requisição que a originou, então eu reabro o escopo do contexto explicitamente na thread de trabalho. Sem isso, aquele trecho aparecia como um rastreamento órfão.

### Maven
**O que é:** Ferramenta de build e gerenciamento de dependências da Apache, voltada para a plataforma Java.
**Para que serve:** compilar, resolver dependências, rodar testes e empacotar.
**No Nexulor:** é um monorepo multimódulo: um POM pai declara as versões (protobuf, gRPC, Resilience4j, Testcontainers, Brave, Spring Cloud) e cada serviço é um módulo que herda dessas versões. Assim, os quatro serviços compartilham exatamente a mesma árvore de dependências.

### Maven Wrapper (`mvnw`)
**O que é:** Scripts que baixam e usam uma cópia específica do Maven junto do projeto.
**Para que serve:** garantir que qualquer pessoa, ou qualquer máquina de integração contínua, use exatamente a mesma versão do Maven.
**No Nexulor:** o comando oficial em todo o projeto é `./mvnw verify`, e é o mesmo comando executado pelo GitHub Actions. Ninguém precisa ter o Maven instalado.

---

## Ecossistema Spring

### Spring Boot
**O que é:** Um framework que monta e configura aplicações Java prontas para produção, com convenção sobre configuração e auto-configuração.
**Para que serve:** eliminar a tediosa configuração de servidor web, pool de conexões, serialização e afins.
**No Nexulor:** é o runtime dos quatro serviços, na versão 3.4. Uma decisão minha foi usar Boot 3.x justamente porque ele já traz instrumentação de observabilidade embutida, o que me pouparia de boa parte do trabalho de rastreamento.

### Spring Web (`spring-boot-starter-web`)
**O que é:** Módulo que expõe controllers REST, usando o servidor embarcado Tomcat.
**Para que serve:** construir APIs HTTP.
**No Nexulor:** é a porta de entrada do serviço de carteira e do serviço de notificação, onde estão os controladores de carteira, transferência e extrato.

### Spring WebFlux (`spring-boot-starter-webflux`)
**O que é:** Módulo reativo do Spring, com Netty e o padrão Reactor.
**Para que serve:** aplicações que precisam de milhares de conexões concorrentes com pouco uso de CPU, como proxies e agregadores.
**No Nexulor:** é a base do API Gateway. Um gateway é essencialmente um proxy, e o modelo reativo é o que torna isso barato em termos de recursos.

### Spring Data JPA
**O que é:** Camada que traduz Orientação a Objetos em SQL relacional.
**Para que serve:** persistir objetos em banco relacional sem escrever SQL à mão na maioria dos casos.
**No Nexulor:** é a porta de persistência do domínio de carteira. E aqui eu escantei a regra mais importante dessa camada: acesso a repositório existe na camada de infraestrutura, e o domínio só conhece a interface da porta. Nunca em `@Repository` no domínio.

### Spring Data MongoDB
**O que é:** Camada de acesso ao MongoDB, com API de repositório igual à do JPA.
**Para que serve:** persistir documentos.
**No Nexulor:** é usada em dois lugares: o serviço de fraude grava a auditoria das decisões de risco, e o serviço de notificação guarda o extrato projetado. Em nenhum dos dois o documento é a fonte da verdade do dinheiro.

### Spring Data Redis
**O que é:** Integração do Spring com o Redis, com abstração de template e de repositório.
**Para que serve:** acessar estruturas de chave-valor de forma idiomática.
**No Nexulor:** é usada para dois fins bem distintos: o registro de idempotência das transferências, com um script Lua atômico, e o contador de velocidade entre instâncias do serviço de fraude.

### Spring Kafka
**O que é:** Integração do Spring com o Apache Kafka, com contêineres de ouvinte (listener), templates e suporte à propagação de cabeçalhos de rastreamento.
**Para que serve:** produzir e consumir mensagens de forma confiável, com reprocessamento e múltiplos consumidores.
**No Nexulor:** o serviço de carteira publica o evento `transaction-completed` depois do commit da transação, e o serviço de notificação o consome e projeta o extrato. A observação habilitada nesta biblioteca é o que permite que o evento carregue o contexto de rastreamento.

### Spring Security
**O que é:** Framework de segurança do Spring, que cobre autenticação, autorização e proteção dos pontos de acesso.
**Para que serve:** decidir quem pode chamar o quê.
**No Nexulor:** fica no gateway, não nos serviços. Escritas exigem token válido; leituras, GraphQL e pontos de acesso de saúde são públicos. A autorização fina, como "este extrato é deste usuário", é responsabilidade da camada de aplicação.

### Spring Security OAuth2 Resource Server
**O que é:** Módulo que transforma uma aplicação em servidor de recursos do OAuth 2.0, validando tokens JWT assinados.
**Para que serve:** autenticação sem sessão e sem consulta a um banco de autenticação a cada requisição.
**No Nexulor:** é a implementação do JWT no gateway. Localmente uso um segredo compartilhado com algoritmo simétrico; em produção, a chave pública do provedor de identidade com algoritmo assimétrico. O ponto que eu quero destacar: o código de validação é idêntico nos dois casos, a diferença fica só na configuração.

### Spring Cloud Gateway
**O que é:** Gateway roteador reativo, construído sobre WebFlux.
**Para que serve:** centralizar na borda a autenticação, o roteamento, o controle de taxa e a observabilidade.
**No Nexulor:** é a porta de entrada única. Todo cliente passa por ele, e nenhum serviço interno é chamado diretamente de fora.

### Spring for GraphQL (`spring-boot-starter-graphql`)
**O que é:** Integração do Spring com GraphQL, com execução de consultas contra controllers Java.
**Para que serve:** permitir que o cliente declare exatamente quais campos quer.
**No Nexulor:** o gateway oferece uma consulta que junta o saldo, as transferências e o extrato em uma única resposta, buscando as duas fontes em paralelo. Se a fonte do extrato cair, a resposta vem com o resto dos dados e uma lista de erros parciais, em vez de falhar inteira. Esse padrão se chama degradação graciosa e é essencial aqui, porque o extrato é por definição o dado mais sujeito a atraso.

### Spring Validation (Jakarta Bean Validation)
**O que é:** Biblioteca de anotações para validar entrada de dados.
**Para que serve:** rejeitar requisições malformadas antes de elas chegarem à lógica de negócio.
**No Nexulor:** valida o corpo das requisições de carteira e transferência. E vale dizer onde ela não é usada: as invariantes de negócio, como saldo suficiente e moeda compatível, não usam Bean Validation, moram no domínio.

### Spring Boot Actuator
**O que é:** Módulo que expõe pontos de acesso operacionais de um serviço, como saúde e métricas.
**Para que serve:** descobrir se o serviço está vivo e como ele está se comportando.
**No Nexulor:** fornece o ponto de acesso de saúde que o Docker e o Kubernetes usam para decidir se um contêiner de aplicação está pronto, e é público no gateway por necessidade operacional.

### Micrometer Observation
**O que é:** API de instrumentação do Spring, que registra medições e rastreamentos sob uma abstração única.
**Para que serve:** fazer com que métricas e rastreamento sejam coletados da mesma forma, sem cada biblioteca reinventar o método.
**No Nexulor:** o serviço de notificação cria uma observação explícita chamada `transaction-completed.project` ao redor do trabalho de projeção, o que aparece no Zipkin como um trecho aninhado ao consumo.

---

**Continua na Parte 2:** PostgreSQL, Flyway, MongoDB, Redis, Lua, Kafka, KRaft, Caffeine, HTTP, GraphQL, gRPC, Protobuf, JWT, OAuth 2.0, JWKS e W3C Trace Context.