# Parte 11 — Testes: a parte que ninguém quer fazer e todo mundo precisa

Agora vamos falar de testes, e eu quero começar com uma provocação.

Existe uma crença muito difundida de que testes automatizados são sobre cobertura de código, ou seja, sobre passar por todas as linhas. Eu discordo, e a discordia é o motivo de eu ter reformulado a estratégia de testes deste projeto inteiro.

A pergunta certa não é "quantas linhas meu teste executa". A pergunta certa é "qual classe de bug meu conjunto de testes é capaz de encontrar". E existe uma classe de bug que nenhum teste com simulação de banco de dados vai encontrar nunca.

Vou dar o exemplo concreto, porque ele é a razão de existirem os testes de integração deste projeto.

Considere a idempotência. O código pergunta ao Redis se a chave já foi vista, grava a chave em voo, e depois grava o resultado. Suponha que você escreva um teste usando uma implementação em memória do Redis, que é um mapa com sincronização. Esse teste passa. E não encontra nada, porque os bugs do Redis real não estão no mapa.

O Redis real usa execução de script de forma atômica, tem expiração automática de chaves, e tem um comportamento específico quando o servidor cai no meio de uma operação. Quando você implementa a coisa com um mapa em memória, você não está testando a mesma coisa. Você está testando a sua própria idealização, e ela está errada por construção, porque você escreveu a implementação do zero e ela faz o que você espera.

O mesmo vale para o Kafka, cuja semântica de entrega é pelo menos uma vez, e para o Mongo, que tem suas próprias corridas em projeções.

Então a regra que eu adotei é: as regras de negócio são testadas com testes unitários, rápidos e sem contexto de aplicação. E os limites com o mundo externo são testados contra infraestrutura real, em contêineres descartáveis, criados e destruídos pelo próprio teste.

No Nexulor isso significou usar Testcontainers, que sobe um Redis de verdade, um Mongo de verdade, e um broker Kafka de verdade dentro do próprio teste. São dois tipos de teste com nomes diferentes, porque são executados em fases diferentes da construção. Os unitários são executados logo depois de compilar. Os de integração, cujo nome termina com as letras I e T, são executados na fase de verificação, depois que tudo já compilou.

O resultado é um sistema com sessenta e cinco testes, sendo cinquenta e sete unitários e oito de integração. E o tempo total de execução fica em torno de um minuto e quarenta segundos, o que é aceitável para rodar a cada alteração.

Agora, o que cada teste de integração realmente prova? E essa é a parte que eu acho mais valiosa de contar.

O teste de idempotência, contra o Redis real, prova que uma chave repetida devolve o resultado guardado e não debita de novo, que uma chave em voo gera conflito, e que a operação é atômica diante de concorrência real.

O teste de publicação de evento, contra o Kafka real, prova que o evento é publicado depois do commit e que chega ao tópico com o conteúdo esperado.

O teste de velocidade, contra o Redis real, prova que a contagem compartilhada entre instâncias funciona, ou seja, que duas instâncias do serviço enxergam o mesmo contador. E isso é impossível de provar com uma simulação em memória, porque cada instância teria o seu próprio contador.

E o teste de notificação, contra Kafka e Mongo reais, prova o caminho completo: consumir um evento de verdade e projetar o extrato em um banco de verdade.

Agora eu preciso contar um erro meu, porque é o tipo de coisa que só aparece na integração contínua.

Esse teste de notificação rodava verde na minha máquina, durante semanas, e passou a falhar no GitHub Actions. E a causa era uma armadilha de configuração do Spring, que é dessas coisas que eu nunca mais vou esquecer.

O teste sobe um contêiner do Mongo e passa o endereço dele para a aplicação. Só que ele passava esse endereço de uma forma que, no Spring, é a de menor precedência: as chamadas de propriedades padrão. E existe um arquivo de configuração no projeto que define o endereço do Mongo como o da máquina local. Ou seja, a configuração do projeto vencia a configuração do teste, e o aplicativo estava tentando se conectar ao Mongo local, que existia na minha máquina porque eu tinha o ambiente de desenvolvimento rodando, e não existia no servidor de integração contínua.

Ou seja: o teste nunca esteve testando o contêiner. Ele estava testando o meu ambiente local, e só falhou porque o servidor não tinha aquele serviço rodando por acaso. Isso é o que se chama de teste verde falso, e é uma armadilha pior do que um teste vermelho, porque te dá uma confiança que não existe.

A correção foi passar a configuração pela linha de comando, que tem precedência máxima e, o que é mais importante, adicionar uma verificação dentro do próprio teste que falha imediatamente, com mensagem clara, se o contexto não estiver apontando para o contêiner. Ou seja, transformar a armadilha em um guarda-corpo. A regra que eu levo dessa história é simples: quando um teste depende de uma condição externa, ele tem que verificar essa condição, porque o teste que não verifica está apenas documentando aquilo que você imagina.

E sobre cobertura de código, quero ser honesto. O sistema tem cobertura medida, e os números são interessantes: o serviço de notificação, que é pequeno e faz uma coisa só, está em noventa e oito por cento; o gateway está em setenta e seis; o serviço de fraude, em setenta e um; e a carteira, em quarenta e nove.

Esse descompasso me levou a uma conclusão que talvez seja a mais útil desta apresentação. Cobertura de código mede presença, não comportamento. Um teste que passa por toda uma linha de configuração sem provar nada aumenta a cobertura e não aumenta a confiança. O que aumenta a confiança é o teste de integração que prova que a idempotência funciona contra o Redis de verdade, mesmo que a cobertura numérica daquele arquivo seja baixa.

# Parte 12 — Entrega: do notebook ao cluster e à nuvem

Falta falar de como isso sai do meu notebook e chega a um ambiente real. E o tema aqui é: a diferença entre o código funcionar e o sistema ser entregável.

O primeiro nível é o local, com Docker Compose. Um arquivo declara nove serviços: os quatro da aplicação, mais PostgreSQL, Mongo, Redis, Kafka e Zipkin. Com um comando, a pilha inteira sobe na máquina, com verificações de saúde, e o sistema inteiro é demonstrável em menos de dois minutos.

O segundo nível é Kubernetes, com Kustomize. Kustomize é uma ferramenta que permite ter uma base de manifestos e aplicar sobreposições por ambiente, sem duplicar arquivos. A ideia é ter uma definição canônica, e a sobreposição de um ambiente local que injeta o perfil de autenticação de desenvolvimento, enquanto a base permanece neutra, pronta para um ambiente que use um provedor de identidade de verdade.

E esse nível foi validado em um cluster de verdade, um cluster kind, que é um Kubernetes completo rodando dentro do Docker na minha máquina. Não é uma simulação: são pods reais, com agendamento real, com volumes reais.

E foi nesse nível que apareceu o problema mais interessante da fase de infraestrutura. O Kafka, na configuração inicial, era um deployment comum. E ele não subia. O motivo é uma propriedade bem conhecida do Kafka, mas que costuma pegar gente de surpresa: o Kafka exige que os brokers formem um quórum e se registrem uns aos outros por DNS antes de ficar pronto. E, em um serviço normal, o balanceador de carga do Kubernetes não considera um pod pronto até que ele responda à verificação de saúde, e o pod só responde quando o quórum está formado. Isso é um impasse.

A solução foi rodar o Kafka como um StatefulSet, que dá a cada pod um nome estável e um volume estável, e acompanhar de um serviço sem IP, com a opção de publicar endereços de pods que ainda não estão prontos. Em outras palavras: dizer explicitamente ao Kubernetes que, nesse caso específico, queremos que o endereço seja publicado antes da prontidão. E o quórum se forma sozinho, e o cluster sobe.

Esse detalhe me ensina algo que eu levo para fora deste projeto: existe uma classe de problemas em que a plataforma e a aplicação têm visões conflitantes sobre o que é estar pronto. E a solução quase nunca é corrigir a aplicação. É corrigir o contrato entre as duas, mesmo que isso signifique quebrar o padrão.

O terceiro nível é a nuvem, com Terraform, que é uma ferramenta de infraestrutura como código. A ideia é descrever a infraestrutura em arquivos declarativos, e aplicá-los de forma reproduzível. No Nexulor isso cobre uma VPC com sub-redes públicas e privadas, um EKS, o serviço gerenciado de PostgreSQL, o serviço gerenciado de Kafka e o serviço gerenciado de Redis, com o estado remoto em um bucket com trava de concorrência, para que duas pessoas não possam aplicar a mesma configuração ao mesmo tempo.

E aqui tem uma decisão que eu acho muito mais importante do que parece: usar serviços gerenciados para os bancos de dados. Um sistema que usa um banco transacional crítico tem duas opções. A primeira é rodar o banco dentro do cluster. A segunda é usar o banco como serviço, que resolve backup, alta disponibilidade, correção de segurança e monitoramento por conta própria.

Para um projeto que quer demonstrar decisões maduras, a segunda opção é a resposta correta, porque o que realmente diferencia um engenheiro sênior não é configurar um banco com StatefulSet, é saber o que não reinventar. Reimplementar backup e recuperação de desastre de um banco de dados é trabalho de alguém que não deveria estar gastando o seu tempo na lógica de negócio do produto.

E é exatamente essa a mensagem final da parte de entrega. A mesma aplicação, a mesma imagem, roda no meu notebook, em um cluster de teste e, com os manifestos de nuvem, em um ambiente gerenciado. E a única diferença entre os três é a configuração.