# Dicionário de Tecnologias do Nexulor — Parte 3

> Resiliência, observabilidade, testes, infraestrutura e integração contínua.

---

## Resiliência

### Resilience4j
**O que é:** Biblioteca de resiliência para a JVM, da Netflix.
**Para que serve:** tempo limite (timeout), repetição de tentativas (retry) e interruptor de circuito (circuit breaker), de forma declarativa e independente do framework.
**No Nexulor:** envolve a chamada gRPC ao serviço de fraude. O tempo limite impede que a transferência fique pendurada indefinidamente; a repetição absorve falhas transitórias; e o circuito, depois de várias falhas seguidas, deixa de tentar chamar por um tempo. Esse conjunto é o que torna possível a decisão de falhar fechado com segurança: em vez de derrubar o serviço inteiro, o sistema recusa a transferência de forma rápida e explícita.

---

## Observabilidade

### Micrometer Tracing
**O que é:** Camada de instrumentação do Spring Boot para rastreamento distribuído.
**Para que serve:** criar os trechos de rastreamento, chamados de spans, e propagar o contexto entre processos sem acoplar o código à biblioteca de transporte.
**No Nexulor:** é a camada que o código dos serviços conhece. Ela cobre HTTP, gRPC e Kafka, e é ela que garante que um único identificador atravesse os três caminhos.

### Brave
**O que é:** Biblioteca de rastreamento, o tracing, da Zipkin, que coleta os trechos e os exporta.
**Para que serve:** implementar o formato de dados do Zipkin e enviar os trechos por rede.
**No Nexulor:** Brave é o motor que faz o envio. Uma observação importante: a instrumentação de gRPC veio dela, porque a biblioteca do Spring não instrumenta chamadas gRPC por completo.

### Zipkin
**O que é:** Interface web de rastreamento distribuído, mantida pelo projeto Zipkin.
**Para que serve:** visualizar o caminho completo de uma requisição, com uma barra de tempo para cada trecho.
**No Nexulor:** é onde eu validei o sistema de verdade. Olhando um rastro real de transferência, vê-se o gateway autorizando, a chamada à carteira, o cliente gRPC, o servidor de fraude, a publicação no Kafka, e, depois do fim da resposta HTTP, o consumidor de notificação projetando o extrato. Ou seja, o rastro torna visual a decisão de consistência eventual. O trecho órfão da virtual thread só apareceu porque olhei essa tela.

---

## Testes

### JUnit 5
**O que é:** Framework de testes unitário para Java, com suporte a testes parametrizados e extensões.
**Para que serve:** estrutura e execução de testes automatizados.
**No Nexulor:** é a base dos 57 testes unitários, que cobrem as regras de negócio sem subir contexto de aplicação e rodam em milissegundos.

### Mockito
**O que é:** Biblioteca de mocking para Java.
**Para que serve:** substituir uma dependência por um duplo controlado, para testar uma unidade isoladamente.
**No Nexulor:** é usado nas bordas do caso de uso de transferência, substituindo as portas de idempotência, fraude e mensageria. Regra que eu segui sem exceção: domínio e aplicação nunca conhecem Mockito; ele só existe no lado de fora da classe testada.

### AssertJ
**O que é:** Biblioteca de verificações encadeadas, as assertions, fornecida junto com o starter de teste do Spring Boot.
**Para que serve:** escrever verificações legíveis, como `assertThat(entries).hasSize(2)`.
**No Nexulor:** é a biblioteca de verificações padrão em toda a suíte, por ser mais legível que as verificações nativas em casos com coleção.

### Reactor Test e StepVerifier
**O que é:** Biblioteca de teste para fluxos reativos, com o StepVerifier como API principal.
**Para que serve:** verificar o resultado e a sequência de um fluxo reativo, incluindo cancelamento e erros.
**No Nexulor:** é usada no gateway, onde toda a lógica é reativa, para verificar tanto o cenário de sucesso quanto a degradação graciosa, quando uma das fontes da consulta GraphQL falha.

### Awaitility
**O que é:** Biblioteca para esperar por uma condição com tempo limite, em vez de dormir fixamente.
**Para que serve:** tornar os testes assíncronos confiáveis sem introduzir espera fixa, que gera tanto testes lentos quanto testes instáveis.
**No Nexulor:** é usada no teste de integração da notificação, que só pode ser verde depois que o evento percorre o Kafka e a projeção chega ao Mongo. O teste verifica a condição por até trinta segundos, sondando a cada duzentos milissegundos.

### Testcontainers
**O que é:** Biblioteca que sobe dependências reais em contêineres descartáveis para uso em testes.
**Para que serve:** testar contra a infraestrutura de verdade, sem instalar nada na máquina.
**No Nexulor:** é o cerne da estratégia de testes. Redis, Kafka e MongoDB reais sobem dentro do próprio teste. A justificativa está no que as simulações escondem: a atomicidade de um script Lua, a semântica de entrega pelo menos uma vez do Kafka e as corridas de projeção do Mongo não existem em simulação. Um teste com mapa em memória não falha por acaso: ele passa, e te dá uma confiança que não corresponde à realidade.

### Embedded Kafka
**O que é:** Servidor Kafka real, o broker, em modo embarcado, iniciado pelo próprio teste.
**Para que serve:** testar publicação e consumo sem subir um cluster externo.
**No Nexulor:** é usado nos testes de publicação de evento do wallet e no de consumo da notificação, em modo KRaft e com um único broker.

### Maven Surefire e Maven Failsafe
**O que é:** Plugins do Maven que executam testes em fases diferentes da construção, o build.
**Para que serve:** separar o que é rápido do que é pesado. Surefire roda os testes unitários na fase de testes; Failsafe roda os de integração na fase de verificação.
**No Nexulor:** a separação é por sufixo de nome. Classes terminadas em IT vão para o Failsafe, e por isso os 8 testes de integração só executam no comando `verify`, nunca em um `test` rápido. Essa é a razão de o README pedir `./mvnw verify` e não apenas compilar.

### JaCoCo
**O que é:** Ferramenta de cobertura de código para Java.
**Para que serve:** medir quais linhas e quais ramos de decisão, os branches, foram executados pelos testes.
**No Nexulor:** gera relatório de cobertura por módulo na fase de verificação, e os números alimentam o selo no README. E aqui vai a ressalva mais importante: cobertura mede presença, não comportamento. O serviço de carteira tem a menor cobertura numérica e é justamente o que tem a regra de negócio mais densa, porque boa parte do que está lá é configuração declarativa. O que de fato prova qualidade aqui é o teste de integração, não o percentual.

---

## Infraestrutura e entrega

### Docker
**O que é:** Plataforma de conteinerização.
**Para que serve:** empacotar a aplicação e suas dependências em uma unidade isolada e reproduzível.
**No Nexulor:** cada serviço tem seu próprio Dockerfile, em múltiplos estágios. O primeiro estágio compila com Maven, e o segundo contém apenas o JRE e o artefato. A imagem final roda com um usuário sem privilégios, e não como root, e com a memória da JVM limitada por porcentagem, em vez de valor fixo.

### Docker Compose
**O que é:** Ferramenta da Docker para declarar e orchestrar múltiplos contêineres a partir de um arquivo.
**Para que serve:** subir um ambiente completo com um comando, de forma determinística.
**No Nexulor:** é o caminho local. Um arquivo sobe nove contêineres, que são os quatro serviços mais PostgreSQL, Mongo, Redis, Kafka e Zipkin, todos com verificação de saúde. Em menos de dois minutos o sistema inteiro está demonstrável.

### Kubernetes
**O que é:** Orquestrador de contêineres, que mantém aplicações desejadas em execução.
**Para que serve:** agendar, descobrir e curar cargas de trabalho, as workloads, com um estado desejado declarado.
**No Nexulor:** é o segundo nível de entrega. As cargas de trabalho são definidas por tipos específicos, como Deployment para as aplicações sem estado e StatefulSet para as que precisam de identidade e volume estáveis.

### Kustomize
**O que é:** Ferramenta de gerenciamento de configuração do Kubernetes, integrada à ferramenta oficial.
**Para que serve:** manter uma base canônica de manifestos e gerar variantes por ambiente sem duplicar arquivos.
**No Nexulor:** a base é neutra, e a sobreposição do ambiente local injeta o perfil de autenticação de desenvolvimento. Assim, a mesma base serve para um ambiente que usa um provedor de identidade de verdade.

### kind (Kubernetes in Docker)
**O que é:** Ferramenta que cria clusters Kubernetes reais, rodando nós como contêineres.
**Para que serve:** validar manifestos em um cluster de verdade, sem custo de nuvem.
**No Nexulor:** foi aqui que a implantação foi validada de ponta a ponta, com nove contêineres de aplicação prontos, transferências concluídas, projeções chegando ao Mongo e um único identificador de rastro no Zipkin. E foi aqui que apareceu o problema mais interessante: o Kafka não subia, porque precisa de resolução de nomes entre os contêineres de aplicação para formar o quórum, enquanto o balanceador do Kubernetes só publica o endereço de um contêiner depois que ele está pronto. A solução foi rodar o Kafka como StatefulSet com um serviço sem IP e a opção de publicar endereços de contêineres ainda não prontos, quebrando de propósito aquele padrão.

### Terraform
**O que é:** Ferramenta de infraestrutura como código, da HashiCorp.
**Para que serve:** descrever a infraestrutura de forma declarativa e reproduzível, aplicando e destruindo conforme necessário.
**No Nexulor:** descreve a topologia na nuvem, incluindo rede, cluster gerenciado, banco gerenciado, serviço de Kafka gerenciado, cache gerenciado, identidade de carga e estado remoto com trava de concorrência. A decisão de fundo é usar serviços gerenciados para os bancos: o que diferencia um engenheiro maduro não é configurar um banco com StatefulSet, é saber o que não reinventar.

### Amazon Web Services
**O que é:** Maior plataforma de nuvem pública.
**Para que serve:** hospedar a infraestrutura com alta disponibilidade, backup e segurança gerenciados.
**No Nexulor:** a topologia inclui VPC com sub-redes públicas e privadas e saída para a internet, cluster EKS gerenciado com grupos de nós e identidade de carga para cada serviço, banco PostgreSQL gerenciado com credenciais no Secrets Manager, serviço de Kafka gerenciado com TLS, cache Redis gerenciado, complemento de armazenamento em blocos, o add-on, para os volumes do Mongo, e estado do Terraform em um bucket com trava de concorrência. Esse conjunto está documentado em um ADR próprio, com a troca explícita: auto-hospedar bancos seria cerca de dez vezes mais barato, e não vale a pena para a escala do projeto.

### GitHub Actions
**O que é:** Serviço de integração contínua integrado ao GitHub.
**Para que serve:** executar verificações automatizadas a cada alteração de código.
**No Nexulor:** roda a cada envio e a cada pedido de integração, usando a distribuição Temurin do JDK 21 e cache do Maven por hash dos arquivos de POM. O comando executado é exatamente o mesmo da máquina, o de verificação completo, o que inclui os 65 testes, entre eles os de integração rodando contra os contêineres da própria máquina de integração contínua. Os relatórios de teste sobem como artefato quando a execução falha.

---

**Fim do dicionário.** São 56 tecnologias e conceitos catalogados, das mais básicas, como o conceito de transação, até as mais específicas, como o padrão de contexto de rastreamento do W3C e o ciclo de vida dos deslocamentos de consumidor, o consumer offset, do Kafka.