# Parte 5 — Idempotência: a mesma requisição, muitas vezes

Agora vamos falar de idempotência, que é o segundo grande pilar do sistema.

O problema é mais comum do que parece. O usuário aperta o botão de transferir. A requisição sai. A rede do celular oscila. O servidor processa tudo direitinho, debita o dinheiro, grava a transferência, e devolve a resposta. Mas a resposta se perde no caminho de volta. O aplicativo cliente não sabe se deu certo. O usuário, impaciente, aperta o botão de novo.

Agora existem dois cenários possíveis, e o cliente não sabe qual é. No primeiro, a primeira requisição nunca chegou no servidor, e esse novo envio é a primeira execução real. No segundo, a primeira requisição foi processada por completo, e esse novo envio é uma duplicata.

Se você não tratar isso, há uma chance real de o usuário ser debitado duas vezes por uma transferência de cinquenta reais. Ou seja, cem reais saem em vez de cinquenta. Esse é exatamente o tipo de bug que destrói a confiança num sistema financeiro.

A solução tem um nome famoso: idempotência. Uma operação é idempotente quando, executada várias vezes com a mesma entrada, produz o mesmo efeito que uma execução única.

Como é que o Nexulor garante isso na prática? O cliente precisa mandar uma chave de idempotência num cabeçalho HTTP chamado Idempotency-Key. É um identificador único que o cliente gera por operação. Quando o servidor recebe essa chave, ele responde a uma de três situações.

A primeira é a situação normal: a chave é nova. O servidor registra a chave como em voo, executa a transferência, e no final guarda o resultado junto com a chave, por vinte e quatro horas.

A segunda é o replay: a chave já foi vista e a operação terminou com sucesso. O servidor não executa de novo. Ele devolve a resposta armazenada, com o mesmo código, com o mesmo corpo, com o mesmo identificador de transferência. Do ponto de vista do cliente, é como se a requisição tivesse sido executada agora. Mas o dinheiro não foi movido de novo.

A terceira é o conflito: a mesma chave chegou com um conteúdo diferente. Isso é um erro do cliente, que está reutilizando uma chave por engano para outra operação. O servidor responde com um conflito, e está certo em agir assim, porque o comportamento alternativo seria devolver silenciosamente o resultado de uma transferência diferente, o que seria muito pior.

E tem um quarto caso, que é o mais interessante: a mesma chave chegou enquanto a operação anterior ainda está em andamento. Isso significa que duas requisições com a mesma chave estão chegando ao servidor ao mesmo tempo, provavelmente porque o cliente está em um laço de novas tentativas agressivo. O servidor responde com conflito de requisição em voo, e isso está correto também, porque executar as duas seria o dobro do dinheiro.

Agora, a pergunta de arquitetura que eu quero que você leve é: por que guardo esse estado no Redis, e não no banco de dados?

A resposta tem duas partes. A primeira é latência e contenção. Se esse estado vivesse no banco transacional, cada tentativa de transferência geraria escrita e leitura na mesma tabela que já está sob travamento pessimista. Ou seja, eu estaria adicionando contenção exatamente na estrutura que escolhi para ser consistente. O Redis tem operações atômicas que resolvem isso em tempo de submilissegundo, e não competem com as travas de linha.

A segunda parte é mais interessante do ponto de vista conceitual: o estado de idempotência não é verdade do domínio. Ele não representa saldo, não representa transferência, não tem invariante de negócio. Ele é um registro técnico, efêmero por natureza, com prazo de validade. Guardar registro técnico efêmero em um armazenamento otimizado para chave-valor é mais coerente do que sobrecarregar o banco relacional com ele.

E aqui tem um detalhe de engenharia que vale a pena contar. A verificação da chave de idempotência não é feita com duas operações separadas, ler e depois escrever, porque isso criaria uma janela de corrida entre duas requisições simultâneas. É feito com um único script Lua, que o Redis executa de forma atômica. O script faz a verificação e a gravação da chave em voo em uma operação só, e devolve o estado encontrado: em voo, replay concluído, ou conflito. Essa é uma técnica que vale a pena conhecer: quando você precisa de atomicidade em um banco de dados que não oferece transações, você empurra a lógica para o servidor de dados com um script.

Na configuração, a trava de execução em voo vive por trinta segundos. É um prazo curto, porque ele existe apenas para cobrir o tempo de uma requisição. Já o resultado da operação vive por vinte e quatro horas, porque é contra essa janela que o cliente pode reenviar. E esses dois prazos estão configurados como parâmetros, não fixos no código.

Com idempotência resolvida, a transferência está correta mesmo sob concorrência e mesmo sob reenvio. Agora vamos falar do serviço que decide se a transferência deve acontecer.

# Parte 6 — O serviço de fraude e a fronteira gRPC

O serviço de antifraude nasceu de uma pergunta simples: por que o motor de regras está dentro do serviço de carteira?

A resposta que eu dei é: porque a taxa de mudança dessas duas coisas é completamente diferente. O código do livro-razão e da carteira é estável. Ele muda quando o negócio muda, e o negócio de carteira muda uma vez por ano, se tanto. Já o motor de regras de fraude muda toda semana. Toda semana surge um novo padrão de golpe, um novo limite, uma nova regra. Se essas duas coisas tivessem na mesma publicação, cada mudança na antifraude seria um risco de mudar o núcleo financeiro. E o inverso também: cada mudança no financeiro atrasaria uma regra antifraude, o que é um risco que ninguém aceita num sistema de pagamentos.

Então extraí o motor de regras para um serviço separado, com seu próprio ciclo de publicação, seu próprio banco, seus próprios recursos de monitoramento. E essa extração é uma decisão que eu recomendo a qualquer pessoa que vá fazer um sistema real: extraia o que muda rápido e é fácil de isolar, não o que é difícil e acoplado.

Agora, a pergunta seguinte: como o serviço de carteira fala com o serviço de fraude?

A primeira opção que todo mundo considera é REST, uma chamada HTTP. E é uma opção legítima. Mas aqui a resposta foi gRPC, e vale a pena explicar por quê.

gRPC é um protocolo de chamada remota de alto desempenho, construído em cima de HTTP dois, com a comunicação descrita por um arquivo de contrato. Em vez de um JSON mais ou menos padronizado, o contrato é um arquivo protobuf, com tipos estritos: o campo é um inteiro, não uma string que às vezes é número e às vezes é texto. E esse arquivo é a fonte da verdade.

O termo para isso é contrato primeiro, que em inglês se diz contract first. Você escreve o contrato antes de escrever qualquer implementação, e as classes de cliente e servidor são geradas a partir dele. Isso tem uma consequência prática enorme: se os contratos de duas equipes não batem, a compilação quebra, antes de qualquer publicação em ambiente. O erro aparece em tempo de compilação, e não em produção, às três da manhã, como uma mensagem de erro que ninguém entende.

No Nexulor, o contrato vive em um módulo separado, dentro de um monorepo multimódulo. Todos os serviços são compilados juntos no mesmo repositório. Isso permite que o contrato seja versionado junto com o código que depende dele, e que uma mudança incompatível no contrato quebre a compilação de todo mundo imediatamente.

E quais são os outros motivos concretos para ter escolhido gRPC aqui? Primeiro, o limite de tempo por chamada, que em inglês se diz deadline. O gRPC permite que o cliente diga: eu não espero mais que trezentos milissegundos. Com REST isso existe, via tempo limite de conexão, mas é mais difícil de expressar e de propagar em toda a camada. Segundo, o envio contínuo de mensagens, o streaming, que não é usado aqui, mas deixa a porta aberta para casos como notificações de longa duração. Terceiro, e talvez o mais importante: a geração automática de código elimina uma classe inteira de bugs de serialização, em que o lado do servidor acha que o campo se chama amount e o cliente manda amountCents.

E há um detalhe de threads que vale muito a pena contar, porque ele é o tipo de coisa que só aparece quando você usa a versão moderna do Java.

O Java vinte e um introduziu as virtual threads, que são threads leves, gerenciadas pela máquina virtual, e que permitem escrever código de bloqueio comum com a escalabilidade de código reativo. No Nexulor, a chamada gRPC ao serviço de fraude roda dentro de uma virtual thread.

Só que tem um detalhe. Quando você troca de thread, o contexto da requisição não vai junto. A virtual thread não sabe qual requisição HTTP originou aquela chamada. Isso significa que, do ponto de vista de observabilidade, aquele trecho de código aparece como um rastreamento separado, órfão, sem relação com a requisição que o chamou. A telemetria fica quebrada justamente no lugar onde você mais precisa dela.

A solução no Nexulor é explícita: dentro da thread de trabalho, eu reabro o escopo do contexto de rastreamento com uma chamada que busca o contexto corrente e o reaplica na nova thread. É uma linha de código que parece um detalhe e na verdade é a diferença entre ter um rastreamento correto e ter um rastreamento enganoso. E é o tipo de bug que só aparece quando alguém olha os rastreamentos de verdade, como eu fiz quando construí a demonstração animada do projeto.

Antes de falar de resiliência e das regras, quero falar de uma decisão de negócio que apareceu naturalmente no desenho do serviço de fraude, e que é das mais interessantes do projeto inteiro.