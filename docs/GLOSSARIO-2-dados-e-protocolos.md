# Dicionário de Tecnologias do Nexulor — Parte 2

> Dados, mensageria e protocolos de comunicação.

---

## Bancos de dados e armazenamento

### PostgreSQL
**O que é:** Banco de dados relacional de código aberto, mantido pela Fundação PostgreSQL.
**Para que serve:** guardar dados com integridade referencial e transações ACID.
**No Nexulor:** é a fonte da verdade do dinheiro. Só aqui vivem as carteiras, os saldos e as transferências, e é o único lugar do sistema em que eu escolhi consistência forte.

### `SELECT ... FOR UPDATE` (lock pessimista)
**O que é:** Cláusula SQL que trava a linha lida até o fim da transação.
**Para que serve:** evitar atualizações perdidas quando duas transações alteram a mesma linha.
**No Nexulor:** a transferência trava as duas carteiras com essa cláusula antes de debitar e creditar. E para nunca travar em ordens diferentes, o serviço ordena os dois identificadores em ordem crescente antes de travar. Essa ordem total é o que impede interbloqueios de forma estrutural, e não por tentativa e erro.

### Flyway
**O que é:** Ferramenta de versionamento de schema de banco de dados.
**Para que serve:** manter o esquema do banco versionado no repositório, junto com o código.
**No Nexulor:** as migrações do banco da carteira ficam no repositório e são aplicadas automaticamente na subida da aplicação. Isso elimina a classe de erro em que alguém lembra de atualizar o banco em um ambiente e esquece em outro.

### MongoDB
**O que é:** Banco de dados documental, que armazena documentos JSON.
**Para que serve:** dados cujo formato muda, ou que se apoiam naturalmente em documentos.
**No Nexulor:** é usado em dois papéis, e em nenhum deles como fonte da verdade. O serviço de fraude grava a auditoria das decisões de risco, e o serviço de notificação guarda o extrato, que é uma projeção. O dinheiro nunca sai daqui.

### Redis
**O que é:** Armazenamento de chave-valor em memória, da Redis Inc.
**Para que serve:** operações atômicas e extremamente rápidas, com estruturas de dados como hashes, contadores e conjuntos ordenados.
**No Nexulor:** tem dois usos deliberados e diferentes. O primeiro é o registro de idempotência das transferências, incluindo a trava de execução em voo. O segundo é o contador de velocidade do antifraude, que precisa ser compartilhado entre instâncias. Em ambos, a escolha se justifica porque o estado é técnico e efêmero, e não verdade de negócio.

### Lua (script no Redis)
**O que é:** Linguagem de script embutida que o Redis executa de forma atômica.
**Para que serve:** executar várias operações como se fossem uma, sem que outra execução se intercale no meio.
**No Nexulor:** a verificação da chave de idempotência usa um script Lua. Verificar se a chave existe e registrá-la como em voo são dois passos, e feitos separadamente criariam uma janela em que duas requisições simultâneas passariam. No script, isso é uma operação indivisível.

### Apache Kafka
**O que é:** Plataforma de fluxo de dados distribuído, o streaming, com alta vazão.
**Para que serve:** desacoplar produção e consumo de eventos, com retenção e reprocessamento.
**No Nexulor:** transporta o evento `transaction-completed`, publicado pelo serviço de carteira depois do commit e consumido pelo serviço de notificação. Kafka entrega cada mensagem pelo menos uma vez, e é por isso que o consumidor precisou ser idempotente.

### KRaft
**O que é:** Modo de operação do Kafka sem ZooKeeper, onde o próprio cluster se coordena por consenso Raft.
**Para que serve:** simplificar a operação do Kafka, removendo o coordenador externo.
**No Nexulor:** é o modo em que o servidor do Kafka, o broker, roda, tanto no Compose quanto nos manifestos de Kubernetes, e o mesmo modo é usado pelos servidores embarcados nos testes de integração.

### Caffeine
**O que é:** Cache local de alta performance, para a JVM.
**Para que serve:** guardar dados em memória no próprio processo, com expiração controlada.
**No Nexulor:** tem um papel honestamente menor. Existe um contador de velocidade local, baseado em Caffeine, que é o padrão quando o serviço roda sem o perfil de Redis, útil para execução isolada. Em produção, o contador que vale é o compartilhado, feito com Redis, porque só ele enxerga todas as instâncias.

---

## Protocolos e contratos

### HTTP e REST
**O que é:** HTTP é o protocolo de aplicação da web; REST é o estilo de arquitetura que o usa como interface de recursos endereçáveis.
**Para que serve:** expor e consumir operações remotas de forma ubíqua e simples.
**No Nexulor:** é a interface pública dos serviços de carteira e de notificação, e é também o transporte do gateway. Uma decisão explícita: transferências exigem um cabeçalho de chave de idempotência, o que é uma consequência direta do estilo REST e do problema de reenvio que ele traz.

### GraphQL
**O que é:** Linguagem de consulta para APIs, onde o cliente declara a forma exata da resposta.
**Para que serve:** evitar o problema de buscar dados de mais, ou de menos, do que a tela realmente precisa, e permitir agregar várias fontes em uma chamada.
**No Nexulor:** o gateway oferece uma consulta que junta saldo, transferências e extrato, buscando as duas fontes em paralelo. Quando uma fonte falha, a resposta volta com os dados disponíveis e uma lista de erros parciais, em vez de falhar inteira. O ganho real é esse: a indisponibilidade de um componente opcional não derruba a tela.

### gRPC
**O que é:** Protocolo de chamada remota de alto desempenho, sobre HTTP/2, com contratos descritos em arquivos.
**Para que serve:** comunicação interna entre serviços com tipagem estrita, limites de tempo por chamada e geração automática de código.
**No Nexulor:** é a fronteira entre o serviço de carteira e o serviço de fraude. Escolhi por três motivos: o contrato é gerado a partir de um arquivo e quebra o build se mudar de forma incompatível; existem limites de tempo nativos, essenciais para a decisão de falhar fechado; e a geração automática cria cliente e servidor, o que elimina erros de serialização por construção.

### Protobuf
**O que é:** Formato de serialização binária e linguagem de definição de interfaces, da Google.
**Para que serve:** descrever contratos de chamada com tipos estritos e gerar código em várias linguagens.
**No Nexulor:** o arquivo de contrato do serviço de fraude é a fonte da verdade do sistema. Ele vive em um módulo próprio, dentro do monorepo, de modo que uma mudança incompatível quebra a compilação de todo mundo antes de qualquer implantação em ambiente. A estratégia é contrato primeiro, que em inglês se diz contract first: escrever o contrato antes da implementação.

### JWT (JSON Web Token)
**O que é:** Formato de token assinado, padronizado pela RFC 7519, que carrega conjuntos de declarações.
**Para que serve:** provar identidade e autorização sem consulta a um banco de sessão a cada requisição.
**No Nexulor:** é o token validado pelo gateway. Ele traz o sujeito, o emissor e a validade, e a assinatura impede que alguém o altere. O custo conhecido e aceito é que revogar um token antes do vencimento exige uma lista de revogação ou tokens de curta duração.

### OAuth 2.0
**O que é:** Protocolo de autorização, que delega a um provedor externo a decisão de quem tem acesso a quê.
**Para que serve:** separar a autenticação do seu sistema, permitindo login com provedores consolidados.
**No Nexulor:** o gateway é um servidor de recursos de OAuth 2.0. Ele não emite tokens, apenas valida os que recebe. Essa separação é o que permite trocar um provedor de identidade sem tocar no código dos serviços.

### JWKS e OpenID Connect
**O que é:** JWKS é um documento publicado pelo provedor de identidade com as chaves públicas; OpenID Connect é a camada de identidade sobre OAuth 2.0.
**Para que serve:** validar tokens assinados sem precisar compartilhar segredo com quem valida.
**No Nexulor:** em produção, o gateway busca as chaves públicas do provedor e valida com algoritmo assimétrico, em que qualquer um verifica mas só o provedor assina. Localmente uso um segredo simétrico. O mesmo código de validação atende os dois casos.

### W3C Trace Context
**O que é:** Padrão do consórcio W3C para propagar contexto de rastreamento entre serviços, com um cabeçalho chamado `traceparent`.
**Para que serve:** manter um identificador de correlação que atravessa as fronteiras entre serviços sem precisar ser passado manualmente em cada chamada.
**No Nexulor:** é o que costura os três caminhos, HTTP, gRPC e Kafka, em um único rastro. Em HTTP e gRPC o padrão define o cabeçalho; em Kafka, que não tem cabeçalhos, o mesmo valor é gravado nos metadados da mensagem. Usar um padrão do mercado em vez de um cabeçalho inventado é o que garante que a telemetria continue funcionando quando alguém troca uma biblioteca.

---

**Continua na Parte 3:** Resilience4j, Micrometer Tracing, Brave, Zipkin, JUnit, Mockito, AssertJ, Reactor Test, Awaitility, Testcontainers, Surefire, Failsafe, JaCoCo, Docker, Docker Compose, Kubernetes, Kustomize, kind, Terraform, AWS, GitHub Actions.