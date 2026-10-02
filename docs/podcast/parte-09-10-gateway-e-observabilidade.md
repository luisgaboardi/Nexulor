# Parte 9 — A borda: API Gateway, OAuth2 e GraphQL

Agora vamos subir um nível e falar da borda do sistema, que é o ponto de entrada de tudo.

A primeira decisão aqui é simples de enunciar e difícil de respeitar: os clientes nunca chamam os serviços internos diretamente. Tudo entra por um API Gateway. E os motivos são mais fortes do que parece.

O primeiro motivo é segurança. Se cada serviço validasse seu próprio token, bastaria um desenvolvedor esquecer de colocar a validação em um ponto de acesso novo para criar uma brecha. Com a autenticação concentrada na borda, a chance de um ponto de acesso público não autorizada é uma linha de código esquecida em um lugar só, em vez de uma configuração espalhada por todos os serviços. É a diferença entre segurança por padrão e segurança por atenção.

O segundo motivo é observabilidade. Quando tudo entra por um gateway, existe um único ponto onde você consegue medir requisições, latência e taxa de erro, antes de elas se espalharem.

O terceiro é controle. Trocar a versão do contrato de um serviço deixa de quebrar os clientes, porque o gateway é quem traduz.

O gateway aqui usa Spring Cloud Gateway, que é um roteador reativo, e faz duas coisas: encaminhar requisições REST para os serviços e agregar leituras.

Sobre a autenticação, a escolha foi tratar o sistema como um servidor de recursos de OAuth dois. Em termos práticos, isso significa que o gateway valida um token JWT, um token assinado digitalmente, sem guardar sessão, sem consultar um banco de sessão a cada requisição. O token traz dentro dele quem é o usuário, até quando vale, e uma assinatura que prova que ninguém o alterou.

E isso resolve o problema clássico do CORS e da vida de sessão em microsserviços: o estado fica todo no token, e o servidor não guarda nada.

Duas decisões de detalhe aqui merecem ser explicadas.

A primeira é sobre chaves e algoritmos. Localmente, para facilitar a vida do desenvolvedor, o gateway usa um segredo compartilhado e um algoritmo simétrico, em que a mesma chave assina e verifica. Isso é adequado para desenvolvimento. Em produção, o correto é o oposto: a chave pública do provedor de identidade, com algoritmo assimétrico, que qualquer um pode verificar mas só o provedor pode assinar. O detalhe importante é que o código do serviço não muda em nada. A mesma lógica de validação funciona nos dois casos, e a diferença fica isolada em configuração. Quando eu desenho a fronteira de um componente, eu tento sempre que a diferença entre teste e produção seja uma variável de configuração, e não uma ramificação no código.

A segunda é sobre o que é público e o que exige token. Escritas exigem token: abrir carteira, creditar, transferir. Leituras são públicas, incluindo a consulta de extrato, o GraphQL e os pontos de acesso de saúde. E isso é uma decisão consciente: o extrato de um usuário exige saber qual usuário é, e essa autorização é feita na camada de aplicação, não no gateway. O gateway responde à pergunta de autenticação, quem você é. A aplicação responde à pergunta de autorização, se você pode ver isso.

Agora, a parte que eu achei mais interessante do gateway: o GraphQL.

A motivação é simples. Um aplicativo precisa montar uma tela de resumo da conta. Essa tela mostra o saldo, as últimas transferências e os últimos lançamentos do extrato. Com REST, isso são três chamadas. E o problema clássico de REST nessa situação é o seguinte: o aplicativo precisa fazer as três, e se uma delas falhar, a tela inteira fica pela metade ou a aplicação precisa tratar cada falha separadamente.

O GraphQL resolve isso com uma consulta só, em que o cliente declara exatamente o que quer. O servidor monta a resposta.

Mas, e se uma das fontes falhar? Aqui está a decisão de design que me agrada mais no projeto inteiro. O gateway faz as consultas em paralelo ao núcleo da carteira e à projeção do extrato, e monta uma única resposta. Se a fonte do extrato estiver fora do ar, a consulta não falha. Ela volta com o saldo e as transferências normalmente, e com uma lista de erros parciais dizendo que a parte do extrato não pôde ser obtida.

Ou seja, a degradação é explícita e visível para o cliente, em vez de ser uma falha total ou um resultado silenciosamente incompleto. Isso é especialmente importante aqui, porque o extrato é, por definição, um dado eventualmente consistente. Faz sentido que ele seja também a parte mais provável de estar atrasado ou indisponível, e faz sentido que a tela continue funcionando sem ele, mostrando explicitamente o que não deu.

Esse padrão tem um nome, que é degradação graciosa, e ele deveria ser o padrão em qualquer agregação de dados que envolva fontes com capacidades diferentes. O que você não quer é o oposto: um sistema onde a indisponibilidade de um componente opcional derruba a página inteira.

# Parte 10 — Observabilidade: um único rastro do começo ao fim

Agora chegamos ao assunto que, para mim, é o que separa um sistema que funciona de um sistema que você consegue operar. E é o assunto do qual eu mais aprendi durante a construção deste projeto.

Vamos definir o problema com um exemplo real deste sistema. Um usuário diz que a transferência dele demorou muito. Você abre o sistema de produção e precisa descobrir o que aconteceu. A resposta está espalhada em quatro serviços, em duas tecnologias de comunicação diferentes, e em um banco de cada tipo.

Os logs ajudam, mas eles têm um problema conhecido: o identificador de correlação precisa ser passado manualmente de serviço para serviço, e uma única pessoa esquecer de fazer isso quebra o encadeamento inteiro. Em um sistema com Kafka, pior ainda, porque o consumidor é um processo completamente diferente.

Rastreamento distribuído resolve isso. A ideia é que, quando uma requisição chega, o servidor gera um identificador único, chamado identificador de rastreamento, que em inglês se escreve trace id, e esse identificador viaja com a requisição. Quando o serviço A chama o serviço B, ele repassa esse identificador no cabeçalho da chamada. Quando o serviço B publica um evento, ele coloca o identificador dentro dos metadados do evento. Quando o consumidor lê o evento, ele recupera o identificador. Resultado: você tem um único identificador que atravessa HTTP, gRPC e Kafka, e que permite ver o caminho completo de uma requisição.

O padrão de cabeçalhos que torna isso possível é o traceparent, um padrão do W3C, o mesmo consórcio que padroniza a web. A grande vantagem de usar um padrão, em vez de um cabeçalho inventado, é a interoperabilidade com o restante do mercado.

No Nexulor, a instrumentação usa Micrometer Tracing, que é a camada de instrumentação do Spring Boot, combinada com o Brave, que é a biblioteca que coleta e envia os dados para o Zipkin, que é o servidor onde você visualiza tudo.

O Zipkin é uma interface web onde você pesquisa pelo identificador de rastreamento e vê o caminho completo, com uma barra de tempo para cada trecho. E aqui é que a parte mais bonita deste projeto acontece.

Vou descrever o que você vê quando faz uma transferência, porque é um retrato de todas as decisões que tomamos até aqui.

A requisição chega no gateway e gera um rastreamento. Dentro dele aparecem os trechos do gateway, que o Zipkin chama de spans: o filtro de segurança, a validação do token, e o encaminhamento.

O gateway chama o serviço de carteira, que abre o seu próprio trecho. Dentro dele aparece a chamada gRPC para o antifraude, que é um trecho do cliente. E dentro desse, aparece o trecho do servidor, no serviço de antifraude, que por sua vez mostra a consulta ao Redis para o contador de velocidade. Ou seja, um sinal de vida atravessando quatro serviços.

Depois, ainda dentro do trecho da carteira, aparecem os dois trechos de publicação do evento no Kafka, porque a publicação acontece depois do commit.

E então, e essa é a parte que me faz sorrir quando olho, o trecho do gateway termina. A resposta HTTP foi enviada. Mas o rastreamento continua. Mais adiante, no mesmo rastreamento, aparecem os trechos do serviço de notificação recebendo o evento e projetando no Mongo. Ou seja: a resposta já foi enviada ao usuário, mas o trabalho de verdade ainda está acontecendo em segundo plano.

Esse é o equivalente visual da consistência eventual, e essa é uma das razões pelas quais eu insisto tanto em ter rastreamento distribuído: porque ele torna visível uma decisão de arquitetura que, sem ele, seria apenas uma afirmação teórica no documento.

E para que tudo isso funcione em Kafka, existe um detalhe técnico. No envio da mensagem, a biblioteca de instrumentação injeta os cabeçalhos de rastreamento dentro dos metadados do evento. No recebimento, ela os extrai e reabre o contexto. É o mesmo mecanismo do HTTP, aplicado a mensageria. E o resultado é aquele detalhe que mencionei antes, das virtual threads: sem um passo explícito de reabertura de contexto, aquele trecho apareceria como um rastreamento órfão, sem ligação com a requisição que o causou.

A amostragem está configurada em cem por cento, o que é adequado para a escala de demonstração e caro demais para produção, onde normalmente se amostra uma fração pequena. Mas o importante é que a amostragem é propagada de forma consistente entre os serviços, o que evita o efeito colateral clássico, em que metade dos seus rastreamentos está completa e a outra metade tem buracos inexplicáveis.

E uma última coisa sobre observabilidade que eu aprendi na prática. Quando um rastreamento fica bonito na tela, é sinal de que o sistema está instrumentado. Quando ele fica feio, ou seja, quando aparece um trecho órfão, ou uma lacuna inexplicável no meio do caminho, é sinal de que existe um bug real. Ou seja, a observabilidade não é só uma ferramenta de monitoramento. Ela é uma ferramenta de descoberta de bugs. No meu caso, o trecho órfão da chamada gRPC só apareceu porque eu olhei os rastreamentos. E eu nunca teria olhado se não estivesse tentando construir uma demonstração visual do sistema.