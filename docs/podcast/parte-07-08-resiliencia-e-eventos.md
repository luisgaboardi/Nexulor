# Parte 7 — Resiliência, fail-closed e o motor de regras

A decisão de negócio que eu mencionei é a seguinte: quando o serviço de antifraude está indisponível, o que o sistema deve fazer com a transferência?

Existem duas respostas possíveis, e cada uma reflete uma cultura de produto diferente.

A primeira é aprovar. Se o antifraude não está respondendo, a gente deixa passar. Isso maximiza a disponibilidade e a conversão. Ninguém perde dinheiro por causa de uma indisponibilidade. O custo é que, durante a falha, transferências fraudulentas passam. Ou seja, você aceita risco desconhecido.

A segunda é reprovar. Se o antifraude não está respondendo, a gente não executa. Isso maximiza a segurança. O custo é que transferências legítimas são bloqueadas durante a falha, e o cliente recebe um erro.

O Nexulor escolheu reprovar. A decisão está documentada em um registro de decisão de arquitetura, o quinto do projeto, e o nome dela é fail-closed, ou seja, falha fechada.

E eu quero defender essa escolha com honestidade, porque ela não é obviamente a melhor. A defesa é a seguinte: o custo dos dois erros não é simétrico. O custo de aprovar uma transferência fraudulenta não é um dado perdido; é perda de confiança, é risco de estorno, é incidente regulatório, é a possibilidade de o serviço inteiro ser considerado inadequado para operar com dinheiro de terceiros. O custo de reprovar uma transferência legítima durante uma indisponibilidade é inconveniência, e inconveniência se resolve com uma mensagem de erro clara e uma nova tentativa.

Então a assimetria justifica a escolha. E eu recomendo a qualquer pessoa que projete sistemas de risco: antes de decidir, faça esse exercício de explicitação de custos. A maioria das decisões de disponibilidade versus consistência no mercado são tomadas no escuro, e o exercício de explicitar os números, mesmo que de forma grosseira, muda a qualidade da decisão.

E para que reprovar seja possível, o sistema precisa de resiliência de verdade. É aqui que entra o Resilience4j, uma biblioteca de resiliência para a JVM, que oferece quatro ferramentas que eu uso em conjunto.

O tempo limite, que em inglês se diz timeout, e que define quanto tempo se espera antes de desistir. A repetição de tentativas, o retry, que define quantas vezes tentar de novo, e com que espera entre as tentativas. E o interruptor de circuito, o circuit breaker, que é a mais interessante: um circuito que, depois de um número de falhas consecutivas, abre e deixa de tentar chamar o serviço por um tempo, falhando imediatamente. E o objetivo do circuito não é só proteger o serviço que está fora, é também proteger o serviço que está chamando, porque uma chamada que trava por trinta segundos consome uma thread e, em cascata, derruba o sistema inteiro.

Com isso, o caminho de uma transferência quando o antifraude está fora é: a chamada falha rápido, tenta de novo um número limitado de vezes, o circuito abre, e o servidor responde com um erro explícito de antifraude indisponível. O usuário recebe uma mensagem clara, o dinheiro não sai, e o sistema não entrou em colapso. Isso é o que eu chamo de falhar de forma controlada.

Agora vamos ver as regras em si, que são simples, e é justamente a simplicidade delas que é a mensagem.

A primeira regra é a defesa contra transferência para si mesmo. É barata, é puramente local, não consulta nada, e por isso roda primeiro. A ordem das regras é explícita e deliberada: as baratas rodam antes das caras.

A segunda regra é o teto de valor, que rejeita transferências individuais acima de um limite configurável.

A terceira regra é a de velocidade, e essa é a mais interessante. Ela rejeita quando a mesma carteira de origem acumula um volume alto de transferências dentro de uma janela de tempo. Isso detecta um padrão clássico de fraude, que é a pulverização: alguém que foi hackeado começa a mandar cinquenta transferências pequenas para carteiras diferentes em cinco minutos.

E aqui tem um detalhe de arquitetura que vale a pena destacar. O contador de velocidade tem que ser compartilhado entre todas as instâncias do serviço de antifraude. Se cada instância tiver seu contador local, e eu tiver cinco instâncias, um fraudador pode fazer cinco vezes mais do que o limite e ninguém percebe. Por isso o contador é global, guardado no Redis, o que se chama de contagem de velocidade compartilhada entre instâncias.

A janela deslizante é implementada com um incremento e uma expiração configurada, executados de forma atômica em um script Lua, para que a janela reinicie de forma limpa a cada evento, sem corrida.

E aqui vem uma assimetria de disponibilidade que é uma escolha deliberada. O contador de velocidade funciona em modo permissivo, o que em inglês se diz fail open. Ou seja, se o Redis cair, o contador falha de forma permissiva e a regra de velocidade deixa passar. A justificativa é: um contador quebrado não deve rejeitar todas as transferências legítimas, porque isso transformaria uma falha de infraestrutura em uma indisponibilidade total do produto. E, como observação, as regras de valor e de auto-transferência continuam rodando normalmente.

Perceba o contraste: a decisão de disponibilidade do antifraude como um todo é fechada, mas a regra de velocidade é permissiva. Não há contradição. São decisões tomadas em níveis diferentes, com custos diferentes. A disponibilidade do serviço de antifraude protege o usuário de perder dinheiro; a disponibilidade do contador de velocidade protege o usuário de ficar sem poder transferir dinheiro. Cada um otimiza para o erro que é mais grave naquele ponto.

Por fim, cada avaliação de risco é gravada de forma assíncrona em um banco de documentos, para auditoria. Isso significa que mesmo as transferências aprovadas deixam rastro, o que é um requisito comum em sistemas financeiros.

Com a decisão de risco fechada, a transferência foi aprovada. Agora é preciso contar isso para o resto do mundo.

# Parte 8 — Eventos, Kafka e a projeção do extrato

A transferência, depois de confirmada no banco transacional, gera um fato. E esse fato tem duas naturezas diferentes, e essa diferença é o coração da parte assíncrona do sistema.

Por um lado, existe o saldo, que é verdade. Está no banco transacional, é consistente, é a fonte da verdade, e é fortemente consistente. Por outro lado, existe o extrato, que é uma leitura. É uma projeção derivada, feita para o usuário olhar, e ela pode estar levemente atrasada sem que nada de errado aconteça no dinheiro.

Na maioria dos sistemas, essas duas coisas são a mesma tabela, e por isso o extrato acaba herdando de todas as restrições do caminho transacional. Aqui não. Aqui elas são separadas, e essa separação tem nome: CQRS, que em inglês significa Separação de Responsabilidades entre Comando e Consulta. O comando é a transferência, a consulta é o extrato, e a separação é deliberada.

O extrato é construído a partir de eventos. Quando a transferência é confirmada, o serviço de carteira publica um evento chamado transaction-completed em um tópico do Apache Kafka. O serviço de notificação consome esse evento e projeta as entradas de extrato, uma de débito na carteira de origem e uma de crédito na carteira de destino.

Kafka é uma plataforma de streaming distribuído. O modelo é simples: produtores escrevem eventos em um tópico, que é essencialmente um log somente de acréscimo, e consumidores leem desse log, cada um mantendo sua posição. Essa estrutura de log é o que dá suporte a reprocessamento e a múltiplos consumidores independentes.

E o detalhe de implementação mais importante do lado do mensageiro é o seguinte: o evento é publicado depois do commit, e não antes. Isso significa que o commit no banco acontece primeiro, e só depois o evento é enviado. É tentador fazer o contrário, publicar o evento dentro da transação, e isso é um erro clássico, por um motivo específico: se a transação do banco falhar depois que o evento já foi publicado, o consumidor vai projetar um extrato que mostra um débito que nunca foi confirmado. O usuário vê um débito fantasma. O dinheiro aparece como saiu quando não saiu. E para corrigir, você precisaria de lógica de compensação, que é complexa e fragile.

Publicando depois do commit, você garante que todo evento publicado corresponde a uma transferência que de fato foi confirmada. Se a publicação falhar, o problema passa a ser a sua ausência, e não a sua existência. Ausência de evento tem solução clássica: um processo de reconciliação. Existência indevida de evento, não.

E o consumidor precisa ser idempotente, porque o Kafka entrega cada mensagem pelo menos uma vez. Se o consumidor processa a mensagem e falha logo depois, antes de confirmar o recebimento, aquela mensagem vai chegar de novo. Se não for idempotente, o usuário vai ver o mesmo débito duas vezes no extrato.

No Nexulor, a idempotência do consumidor vem da chave do documento. Cada entrada de extrato tem um identificador derivado do identificador do evento mais a direção, débito ou crédito. Então, quando o mesmo evento é projetado duas vezes, ele substitui o mesmo documento em vez de duplicar. Isso é idempotência por construção, resolvida na camada de persistência em vez de ser implementada com lógica de verificação na aplicação.

E é aqui que a consistência eventual aparece em toda a sua glória. Se você abre o aplicativo e pede o extrato um milissegundo depois de confirmar uma transferência, pode ser que o extrato ainda não tenha esse lançamento. O usuário vê um sistema que responde na hora e, um segundo depois, o lançamento aparece. Em troca, ganhamos um caminho transacional que não depende da disponibilidade de Kafka nem do Mongo, e que não desacelera a transferência.

Esse é o preço, e ele está escrito na documentação de forma explícita, incluindo o que a empresa ganha e o que ela perde. Documentar o custo de uma decisão faz parte da decisão.

Agora, o caminho completo de uma transferência, do começo ao fim, fica assim. O cliente envia a requisição com o cabeçalho de chave de idempotência. O gateway valida o token. O serviço de carteira verifica a chave no Redis. Se for nova, marca em voo. Chama o antifraude por gRPC, com limite de tempo, repetição de tentativas e circuito. Se o antifraude reprovar, a transferência é recusada e a chave é liberada. Se aprovar, abre a transação, trava as duas carteiras em ordem, debita, credita, grava a transferência e a chave de resultado, e fecha com commit. Só então publica o evento no Kafka. E, de forma independente, o serviço de notificação consome o evento e projeta o extrato.

Nenhuma chamada de rede ocorre dentro da transação do banco. Nenhum evento é publicado antes do commit. Nenhuma resposta depende de um serviço que possa estar fora sem que isso esteja explícito. Esse é o nível de disciplina que faz diferença entre um sistema que roda numa demonstração e um sistema que roda em produção.