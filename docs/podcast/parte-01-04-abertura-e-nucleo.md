# Nexulor — Roteiro de Palestra e Podcast

---

# Parte 1 — Abertura

Bem-vindo. Hoje eu quero te contar a história de um projeto que se chama Nexulor, que é uma plataforma de pagamentos que eu construí do zero para estudar engenharia de software de nível sênior de verdade, não só para ter mais um repositório no GitHub.

A ideia é que, ao final, você entenda não apenas o que o sistema faz, mas por que cada peça existe, quais foram as decisões difíceis, quais alternativas eu considerei e por que descartei cada uma, e também quais erros eu cometi pelo caminho. Porque, na minha experiência, um sistema não é definido pelo que funciona bem, e sim pelo que você aprendeu quando alguma coisa quebrou.

Deixa eu começar pelo começo de verdade, pelo domínio.

Imagine que você vai abrir uma conta em um banco digital. Você entra no aplicativo, abre uma carteira, recebe dinheiro, e depois manda uma transferência para a carteira de outra pessoa. É isso, essencialmente, que o Nexulor faz. É um sistema onde você abre carteiras, deposita dinheiro, transfere dinheiro entre carteiras e depois consulta o seu extrato.

Só que essa descrição, que parece simples, esconde uma assimetria brutal. Comparar a mesma frase em dois contextos deixa isso claro. Se você está fazendo um sistema de rede social e um usuário posta uma foto, o pior que acontece se der erro é uma foto perdida. Se você está fazendo um sistema de pagamentos e o usuário transfere dinheiro, o pior que acontece não é um erro de tela. É dinheiro que sai de uma carteira e não chega na outra. Ou pior: é dinheiro que sai duas vezes. Ou é um extrato que mente para o usuário, mostrando um débito que nunca compensou uma entrada.

Por isso, quando se desenha um sistema financeiro, cada decisão técnica tem uma pergunta obrigatória por trás: o que acontece quando duas requisições chegam ao mesmo tempo? O que acontece quando o cliente envia a mesma requisição três vezes porque a rede ficou lenta e ele não sabe se deu certo? O que acontece quando um serviço que estava funcionando de repente cai no meio de uma operação?

Essas três perguntas foram, literalmente, o motor de todas as decisões de arquitetura do Nexulor. Cada escolha que eu fiz no projeto tem uma dessas perguntas como justificativa. Quando eu não tinha resposta para essas perguntas, é porque a escolha estava errada.

E antes de falar de tecnologia, eu quero falar do dinheiro como um problema de engenharia, porque isso é o que diferencia um sistema financeiro de qualquer outro sistema distribuído.

# Parte 2 — Por que dinheiro é um caso especial

Dinheiro é um dos poucos domínios em que três propriedades clássicas da computação entram em conflito direto entre si.

A primeira propriedade é a consistência. Se eu digo que a sua carteira tem cem reais, eu estou afirmando um fato. Esse fato precisa ser verdade para todo mundo, em qualquer nó do sistema, em qualquer instante. Não existe "aproximadamente cem reais". Não existe "a princípio cem reais, mais ou menos". Então consistência é obrigatória.

A segunda propriedade é a disponibilidade. Se o meu aplicativo está fora do ar, eu não vou transferir dinheiro, e isso me custa uma venda. Se um banco fica indisponível, o prejuízo é de outra ordem de grandeza. Então disponibilidade importa muito.

A terceira propriedade é o isolamento, ou seja, duas operações não podem se atrapalhar. Se alguém está depositando na minha carteira enquanto eu estou sacando, uma das duas não pode enxergar a outra pela metade.

No modelo tradicional de teoria de bancos de dados, esses três desejos são chamados de ACID, e o motivo de existirem é justamente o sistema bancário tradicional. O grande desenvolvimento dos últimos trinta anos na computação distribuída foi o movimento oposto: sistemas que aceitam perder consistência temporária em troca de mais disponibilidade e escala. O Google inventou o teorema de CAP, que diz que você tem que escolher entre Consistência, Disponibilidade e Tolerância a Partições de rede, e que só pode garantir dois dos três ao mesmo tempo.

O Nexulor é a demonstração de que essa escolha pode ser feita de forma granular, situação por situação. No mesmo sistema, podemos tomar decisões diferentes para partes diferentes.

Na transferência de dinheiro em si, eu escolhi consistência forte. Uma transação no PostgreSQL com Isolamento padrão, com bloqueios pessimistas. Se duas transferências batem na mesma carteira ao mesmo tempo, uma espera a outra. Isso custa contenção, custa latência em cenários de altíssimo volume, e é uma escolha deliberada. Mas é a escolha correta quando o recurso é dinheiro.

Já na leitura do extrato, que é derivado de um evento, eu escolhi consistência eventual. Você pode ver o extrato meio segundo atrás, e tudo bem, porque o extrato não é a verdade. A verdade é o livro-razão, que está no banco transacional. O extrato é uma projeção, uma leitura materializada, e leituras materializadas podem ser um pouco atrasadas sem que ninguém perca dinheiro.

Então a resposta para a pergunta "qual das três propriedades você escolhe" é: eu escolho todas as três, mas em lugares diferentes, e eu documento exatamente onde cada uma se aplica.

Esse raciocínio, de escolher a consistência por caminho crítico e relaxar fora dele, é o que separa um sistema de demonstração de um sistema que eu levaria para produção.

# Parte 3 — A arquitetura do núcleo: hexagonal, portas e adaptadores

Agora vamos olhar como o código está organizado, porque isso também é uma decisão, e uma decisão com consequências de longo prazo.

O serviço de carteira, que é o núcleo financeiro do sistema, segue a arquitetura hexagonal, também conhecida como arquitetura de portas e adaptadores. A ideia central é muito simples de explicar e muito difícil de respeitar.

A ideia é que o domínio, que é o código que sabe o que significa uma carteira, uma transferência e dinheiro, não deve depender de nada externo. Ele não sabe que existe PostgreSQL, não sabe que existe HTTP, não sabe que existe Redis. Ele apenas sabe: uma carteira tem um dono, um saldo, e um saldo não pode ficar negativo.

Todo o resto do mundo entra por portas. Uma porta, nesse vocabulário, é um contrato. Por exemplo, existe uma porta chamada TransferRepository, que diz: para buscar uma carteira por identificador, para salvar uma carteira, para buscar uma transferência. O domínio diz o que precisa, e não como vai ser feito.

Do outro lado estão os adaptadores. Um adaptador é a implementação concreta dessa porta. O adaptador de persistência é o que fala JPA e SQL. O adaptador de idempotência é o que fala Redis. O adaptador de mensageria é o que fala Kafka. E quando eu amanhã quiser trocar o PostgreSQL por outra coisa, eu troco um adaptador. O domínio nem fica sabendo.

Isso tem um benefício prático imediato no que diz respeito a testes. Os testes unitários do domínio são feitos com implementações em memória, que rodam em milissegundos, sem Spring, sem banco, sem contêiner. Depois existem os testes de integração, que usam a infraestrutura real. A separação é clara.

Dentro do serviço de carteira, olhando a árvore de código, existem cinco pacotes. Api, onde ficam os controladores REST e os contratos de entrada e saída. Application, onde vivem os casos de uso, isto é, o serviço de transferência e o serviço de carteira. Domain, onde estão as entidades e as regras de negócio. Infrastructure, com os adaptadores concretos. E Observability, com a configuração de rastreamento.

Um detalhe que talvez pareça exagero, mas que vale a pena destacar: o domínio tem uma classe chamada Money. Ela é um objeto de valor, e isso significa que a moeda e o valor andam sempre juntos, nunca separados. Existe também o Transfer, que representa uma transferência, e o Wallet, que representa uma carteira.

E aqui está uma coisa que me agrada: o domínio valida a si mesmo. A carteira tem um método de débito que, antes de mudar qualquer coisa, verifica se o saldo é suficiente. Se não for, ele lança uma exceção de saldo insuficiente. E o valor monetário Money, ao ser subtraído, verifica que as moedas são iguais e lança uma exceção de incompatibilidade de moeda se não forem. Ou seja, ninguém consegue, nem por engano, somar reais e dólares. Essa proteção mora no domínio, e não em uma condição solta dentro de um controlador.

Na camada de aplicação mora o caso de uso de transferência. E é ali que mora uma das decisões mais importantes do projeto inteiro, sobre a qual eu vou falar em detalhe agora.

# Parte 4 — Concorrência: o travamento pessimista e a ordem determinística

Vamos falar de concorrência, que é o assunto mais importante deste projeto.

O cenário: uma pessoa tem a carteira com cem reais. Duas requisições de transferência chegam ao mesmo tempo, no mesmo instante, para a mesma carteira. Uma quer mandar cinquenta reais, a outra quer mandar oitenta. O saldo é cem. Só uma pode ser atendida. A outra tem que receber um erro de saldo insuficiente.

Se as duas requisições lessem o saldo ao mesmo tempo, as duas veriam cem, as duas subtrairiam, e o resultado seria um saldo de menos trinta, ou seja, dinheiro negativo. Isso é o que chamamos de uma atualização perdida, e é um bug clássico.

Existem duas famílias de soluções para isso.

A primeira é o controle otimista de concorrência. Você lê o saldo, calcula, e tenta gravar. Ao gravar, você inclui uma condição de versão, e se a versão mudou entre a leitura e a gravação, a gravação falha e você tenta de novo. Isso funciona bem quando os conflitos são raros. E é ótimo para sistemas com muita leitura e pouca escrita.

A segunda é o controle pessimista. Você trava a linha antes de ler. O banco de dados garante que ninguém mais mexe naquela linha até você terminar. No PostgreSQL isso se faz com a cláusula Select For Update. O nome é autoexplicativo: selecione a linha e trave ela para atualização.

O Nexulor usa controle pessimista. E por quê? Porque em um sistema financeiro, o conflito não é exceção, é regra. A carteira de um usuário popular pode receber dezenas de requisições simultâneas. Com controle otimista, você transformaria cada uma delas numa tentativa que falha e é repetida, criando uma enxurrada de retentativas exatamente no pior momento, que é quando há mais tráfego.

E aqui vem o detalhe que me orgulha, e que é o tipo de coisa que só se aprende fazendo.

Quando você trava duas linhas em uma transação, existe o risco de interbloqueio, o que os profissionais chamam de deadlock. O interbloqueio acontece quando a transação A segura o bloqueio da linha um e espera a linha dois, enquanto a transação B segura o bloqueio da linha dois e espera a linha um. As duas esperam para sempre, e o banco acaba abortando uma delas.

A solução clássica é sempre travar na mesma ordem. Por exemplo, sempre em ordem crescente de identificador. Se todas as transações travam na ordem crescente, um ciclo de espera é impossível: se A espera a linha dois e B espera a linha um, isso quer dizer que B começou com um identificador maior que o de A, e então B deveria ter esperado a linha um primeiro. O ciclo não se forma. É a mesma lógica de ordem total usada no travamento de sistemas concorrentes, e uma ordem total impede ciclos por construção.

No Nexulor, isso aparece em duas linhas de código, no serviço de aplicação da transferência. O código coleta os dois identificadores de carteira, ordena em ordem natural, e então trava nessa ordem. Só isso. Duas linhas que resolvem uma classe inteira de interbloqueios.

Mas há um segundo detalhe, e ele é sobre transação versus tempo. O travamento pessimista só vale se o tempo dentro do banco for curto. E no nosso caso, havia uma armadilha: entre abrir a transação e fazer o commit, o serviço faz uma chamada de rede para o serviço de antifraude. Uma chamada de rede pode demorar, pode ter um tempo limite, pode oscilar. Se você segura um travamento de banco durante uma chamada de rede, você multiplica o tempo de retenção do travamento por um fator desconhecido, e a vazão da base de dados despenca.

A solução foi organizar o código para que a chamada ao antifraude aconteça antes de abrir a transação. Ou seja: verificar o risco primeiro, travar e debitar depois. Isso reduz a janela transacional ao mínimo possível: apenas as operações de banco de dados.

Esse detalhe tem um nome bonito na literatura, e é o princípio de "não segure o travamento através de uma chamada remota". Se você ouvir isso em uma entrevista de emprego e souber explicar com um exemplo concreto como a janela transacional se reduz, você já está um nível acima.

Então, para resumir o núcleo transacional: consistência forte com bloqueios pessimistas, ordem determinística para nunca dar interbloqueio, janela transacional mínima sem chamadas de rede dentro da transação, e validação de invariantes mora dentro do próprio domínio, não espalhada pela aplicação.