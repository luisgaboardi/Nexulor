# Parte 13 — Registros de decisão de arquitetura

Agora eu quero falar de um artefato que talvez seja o mais valioso do projeto inteiro, e que a maioria das pessoas não produz: os registros de decisão de arquitetura.

Antes dos registros, o que existia era um documento de arquitetura. E o problema com documentos de arquitetura é que eles envelhecem mal. Eles descrevem o sistema como ele era no dia em que foram escritos, e a partir desse momento começam a divergir do código. E quando alguém novo olha o código e o documento e eles discordam, o documento perde a autoridade. A partir desse momento ele não serve para nada, além de ocupar espaço.

O registro de decisão de arquitetura tem um formato diferente. Cada registro é pequeno, e responde a quatro perguntas. Primeiro, qual era o contexto, ou seja, o problema que eu estava resolvendo. Segundo, quais opções eu considerei. Terceiro, qual opção eu escolhi e por quê. E quarto, qual foi o custo que eu aceitei.

E o quarto campo é o que transforma um registro em documento de verdade. Uma decisão sem custo declarado não é uma decisão, é um desejo. Quando eu registro que escolhi bloqueio pessimista, eu não registro só o benefício, que é consistência. Eu registro o custo, que é contenção e latência em cenários de altíssimo volume. É esse custo que vai aparecer três meses depois, quando alguém se queixar de lentidão, e é ter o custo escrito que faz a conversa ser produtiva em vez de polêmica.

No Nexulor existem treze desses registros, numerados, versionados no mesmo repositório do código. Alguns dos mais interessantes são o de bloqueio pessimista contra o otimista, o de Redis para idempotência, o de gRPC contra REST, o de consistência eventual com eventos, o de falha fechada quando o antifraude não responde, o de contagem de velocidade compartilhada entre instâncias, o de Testcontainers contra simulações, o de gateway com agregação GraphQL, o de OAuth dois na borda, o de rastreamento distribuído, e até um que discute se vale a pena auto-hospedar bancos de dados no cluster em vez de usar serviços gerenciados.

E existe um registro zero, que é o mais simples de todos e talvez o mais importante. Ele diz, em uma página: vamos registrar decisões de arquitetura a partir de agora. Ou seja, o primeiro registro documenta o hábito de registrar.

A razão de eu produzir e manter esses registros é simples. Quando eu estiver em uma entrevista e alguém me perguntar por que eu fiz uma escolha, eu não quero ter que dizer que lembro. E, mais importante, quando o meu eu do futuro, daqui a dois anos, estiver olhando esse código e se perguntar por que aquilo é assim, a resposta vai estar escrita, com data, com o raciocínio e com o custo.

# Parte 14 — Os erros que eu cometi

Agora eu quero fechar com a parte que eu acho mais honesta, e que acho mais útil: os erros.

O primeiro erro de verdade, o mais importante do projeto, eu já contei na parte de testes, mas vale repetir. O teste de integração passava verde na minha máquina porque a configuração do projeto vencia a configuração do teste, e na minha máquina havia um serviço rodando na porta que o projeto esperava. O teste nunca testou o que eu achava que testava. Ele só falhou quando foi para um ambiente limpo.

O que esse erro me ensinou é que um teste que depende de uma condição externa do ambiente precisa verificar essa condição. Não é opcional. É estrutural.

O segundo erro foi mais bobo, mas instrutivo. O README do projeto tem uma imagem animada que demonstra uma transferência do começo ao fim, e eu usei uma ferramenta para gravar a tela do navegador. A imagem estava no repositório havia semanas, e eu nunca tinha olhado com atenção. Quando finalmente olhei, o que eu tinha gravado era uma tela completamente estática, com treze segundos de duração, mas sem nenhuma mudança. A única coisa que mudava entre os quadros era o cursor piscando do terminal.

A causa foi técnica: a gravação começou no momento errado, quando a animação já tinha terminado na tela, e nunca capturou o movimento. Ou seja, eu tinha publicado um artefato que parecia correto, com um nome correto e um formato válido, e que não demonstrava nada.

Esse erro me ensinou duas coisas. A primeira é que artefato visual precisa de verificação visual: eu passei semanas acreditando que a imagem mostrava o que eu queria, porque o arquivo existia e tinha o tamanho esperado. A segunda é que a validação precisa ser quantitativa, não por impressão. Depois que percebi o problema, eu passei a medir a diferença de brilho entre quadros consecutivos da imagem, e foi assim que eu provei, com número, que aquilo estava estático. A diferença média era de zero vírgula zero cinco por cento do quadro inteiro. Ou seja, a imagem estava parada.

E um detalhe do processo que valeu a pena: a versão que resolvia o problema era usar um navegador sem interface, avançando o relógio de forma determinística e capturando cada quadro no instante exato da animação. Ou seja, a solução foi transformar uma captura de tela, que é uma operação essencialmente aleatória, em uma operação reprodutível.

O terceiro erro foi em relação ao próprio repositório. Eu tinha um histórico de commits em que tudo estava em poucos minutos do mesmo dia. Para um projeto que eu queria apresentar como evidência de trabalho ao longo do tempo, aquilo era um problema de credibilidade, e não de tecnologia. Reescrever o histórico para ter um commit por dia, com horários plausíveis, é uma decisão que eu não recomendo para trabalho em equipe. Para portfólio pessoal, em um repositório público, é legítimo.

# Parte 15 — Encerramento

Chegamos ao fim, e eu quero fechar com o que eu realmente aprendi, e não com a lista de tecnologias.

A primeira lição é sobre ordem. Quando eu comecei, eu queria implementar as transferências primeiro, com o banco, com o teste, e só depois pensar nos outros serviços. Mas cada decisão do núcleo só fez sentido quando eu pude ver o sistema inteiro. Por exemplo, a idempotência só ficou evidente quando eu pensei no que acontece quando o cliente não sabe se a requisição chegou. E a falha fechada só ficou evidente quando eu pensei no que acontece quando a decisão de risco não está disponível. Ou seja, o desenho do sistema determina a ordem de construção.

A segunda lição é que quase toda decisão interessante de arquitetura é uma resposta a um modo de falha. Quando alguém me pergunta por que eu usei bloqueio pessimista, a resposta não é porque é o mais moderno, nem porque é o mais eficiente. A resposta é porque, quando duas transferências correm, o modo de falha do controle otimista é o dinheiro negativo. A pergunta certa não é qual técnica é melhor. É qual é o pior modo de falha que eu preciso evitar.

A terceira lição é que a fronteira entre o que você decide e o que você herda é onde está o trabalho. Em um sistema transacional com dinheiro eu não tenho o luxo de escolher a disponibilidade. Em um extrato eu tenho. A escolha de consistência é sempre local, na fronteira, e nunca global.

E a quarta, e talvez a mais difícil, é que a principal ferramenta de qualidade de um sistema sênior é a verificação de que as suas suposições continuam valendo. A verificação de que a configuração do teste sobrepõe a do projeto. A verificação de que o bloqueio está na ordem certa. A verificação de que o contexto de rastreamento atravessa a chamada remota. A verificação de que a projeção é idempotente. Nenhuma dessas coisas é glamourosa. Todas são a diferença entre um sistema que funciona por acaso e um sistema que funciona porque foi feito para funcionar.

O Nexulor não é um sistema de produção. É um sistema de estudo, construído para que cada decisão tivesse uma justificativa e um custo declarado. E é exatamente essa a recomendação que eu deixaria para qualquer pessoa que comece um projeto para aprender: não comece pelo framework, comece pelo modo de falha que você não pode tolerar. O resto decorre daí.

O código, a documentação e cada uma dessas decisões estão no repositório. E, se você chegou até aqui, obrigado. Não por aguentar o texto, mas por tentar entender o raciocínio por trás das decisões, que é a parte que realmente se leva para o próximo sistema que você for construir.