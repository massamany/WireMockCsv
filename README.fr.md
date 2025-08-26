﻿﻿
# Extension Wiremock CSV

![Build status](https://api.travis-ci.com/massamany/WireMockCsv.svg "Build status") 

*Lire cette documentation dans une autre langue : [English](README.md).*

## Introduction

Cette extension WireMock permet de lire des données depuis des CSV avec une syntaxe SQL, dans le but de générer automatiquement les JSon de réponse avec ces données.

## Construire le livrable:

Maven 3 et un JDK 8 sont nécessaires pour construire l'archive. Ensuite, la commande suivante est à lancer :

    mvn clean install

Le répertoire "target" contiendra alors :
* un jar standalone contenant l'intégralité des binaires nécessaires (WireMockCsv-VERSION-standalone.jar).
* un jar semi-standalone contenant l'intégralité des binaires nécessaires excepté wiremock (WireMockCsv-VERSION-with-dependencies.jar).
* un jar contenant seulement le code de l'extension (WireMockCsv-VERSION.jar).

## Lancer l'application en standalone via console:

### Lancement simplifié:

Cette commande permet d'utiliser la main class configurée dans le jar autonome WireMockCsv pour effectuer le lancement.

    java -Dfile.encoding=UTF-8 -jar ".\WiremockCsv-1.2.0-standalone.jar" --port 8181 --root-dir "###MY_PROJECT_PATH###\src\test\resources\mock"

### Lancement toutes options:

Utiliser cette commande pour varier le runner, le classpath, utiliser d'autres extensions, etc ...

    java -Dfile.encoding=UTF-8 -Dcsv-root-dir="###MY_PROJECT_PATH###\src\test\resources\mock" \
    -cp "wiremock-standalone-3.13.1.jar:wiremock-jwt-extension-0.4.jar:wiremockcsv-1.2.0-with-dependencies.jar:wiremock-extensions_2.11-0.15.jar:wiremock-extensions_teads_2.11-0.15.jar:handlebars-proto-4.1.2.jar:wiremock-body-transformer-1.1.6.jar:handlebars-4.1.2.jar" \
    com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --port 8181 --global-response-templating  --verbose  --root-dir "###MY_ROOT_DIR###" \
    --extensions com.wiremock.extension.csv.WireMockCsv,tv.teads.wiremock.extension.JsonExtractor,tv.teads.wiremock.extension.Calculator,tv.teads.wiremock.extension.FreeMarkerRenderer,tv.teads.wiremock.extension.Randomizer,com.opentable.extension.BodyTransformer,com.github.masonm.JwtMatcherExtension,com.github.masonm.JwtStubMappingTransformer

## Lancer l'application en standalone via Eclipse:

Utiliser la configuration de lancement fournie, éventuellement en changeant le répertoire root-dir.

## Changer le chemin de la base CSV:

Si le lanceur fourni permettant à l'extension de récupérer le chemin racine des mocks ne peut pas être utilisé ou si vous voulez le changer, la propriété système suivante peut être utilisée `csv-root-dir`.

## Utilisation:

### Créer sa BDD

Les données de tests sont un bon exemple pour démarrer.
Les étapes de création de la BDD sont :
* Récupérer les fichiers Database.properties et Database.script des exemples.
* Créer ses CSV (un fichier par table, sans ligne d'entête)
* Créer les lignes `CREATE TEXT TABLE PUBLIC.XXX` et `SET TABLE PUBLIC.XXX SOURCE 'xxx.csv;encoding=UTF-8;fs=\semi'` dans Database.script, pour tous les CSV. Les noms de colonnes doivent être entourés de guillemets afin d'être case sensitive, ceci est essentielle pour que les mappings de champs soient corrects.

C'est tout ! :-)

### Créer les mappings CSV

*Remarque préliminaire : Il est possible de mixer les mappings et files standards de WireMock avec les fichiers spécifiques du transformers.*

Le concept est simple: interroger en SQL la BDD CSV, et mapper les résultats en JSON en utilisant les nom de colonnes en tant que nom de champs.

Le "jsonBody" standard est remplacé par 2 valeurs :
* "transformers": ["wiremock-csv"]
* "transformerParameters": { Structure, Requêtage }

La structure permet de définir la structure attendue du résultat et où y sera intégré le résultat du requêtage. L'emplacement du résultat est représenté par un paramètre nommé "${WireMockCsv}".
La structure par défaut est `${WireMockCsv}`, pour simplement renvoyer le résultat en format JSON.

Elle peut être surchargée par plusieurs moyen :

* Configuration globale (voir le chapitre Configuration globale)

* Surcharge spécifique pour une requête (voir les exemples  rechercherFactures ou rechercherFactures2) via la syntaxe suivante :

Exemple de changement de structure :

    "structure": {
      "donnees": "${WireMockCsv}"
    },

Le requêtage permet de :

* Lister les lignes d'une table, éventuellement en filtrant selon les paramètres HTTP.
* Faire des jointures, des sous-select, etc ... sur les données.
* Obtenir une grappe d'objets complexes en résultat, avec sous-objets imbriqués et sous-listes.

### Construire son requêtage

Un requêtage est constitué de plusieurs composants :
* "query" : Une requête SQL, potentiellement paramétrée avec soit les paramètres HTTP (pour la requête principale), soit les résultats de la requête mère (pour les sous-requêtes) :
    * Un paramètre se déclare comme suit : ${monParamètre} ou $[monParamètre] pour échapper les quotes (') dans les chaines de caractères SQL.
    * Requête principale : Il sera remplacé par la valeur paramètre custom du même nom ou à défaut par la valeur du paramètre HTTP du même nom
    * Sous requête : Il sera remplacé par la valeur de la colonne du même nom ou à défaut par la valeur paramètre custom du même nom ou à défaut par la valeur du paramètre HTTP du même nom
    * Un paramètre n'ayant pas de valeur de remplacement sera remplacé par une chaîne vide
* "jsonParamQuery": Pareil que "query", mais en permettant l'extraction de paramètre depuis le body de la requête : ${$..jsonObject}. 
* "conditionQuery" : Une requête SQL ne devant retourner qu'une valeur (une ligne et une colonne). Potentiellement paramétrée avec soit les paramètres HTTP (pour la requête principale), soit les résultats de la requête mère (pour les sous-requêtes).
* "conditions" : Map de valeurs possibles issue de "conditionQuery", pour lesquelles un requêtage personnalisé peut être réalisé. Ceci permet de personnaliser le résultat et éventuellement sa structure, en fonction des données. La valeur prédéfinie suivantes sont gérées :
    * "undefined" si pas de résultat (aucune ligne).
    * "null" si la valeur est nulle (une ligne avec champ null).
    * "default" pour une valeur non nulle non présente dans les conditions.
* "mask" : Une liste des noms de colonnes résultats de la requête principale qui n'apparaîtront pas dans le JSon final, ceci permettant de récupérer des valeurs pour un paramètre de sous-requête. Autre avantage, utiliser le "select *" et supprimer une colonne du résultat afin d'obtenir requête moins verbeuse.
* "aliases" : Alternative pour paramétrer les noms de sous-objets et de champs, évitant d'utiliser les alias de nom colonne.
* "subqueries" : Il s'agit en fait simplement d'une Map de [String ; Requêtage]. La clé est le nom (champ JSon) de la sous-liste à récupérer et le requêtage est encore une fois un ensemble des composants "query", "subqueries", etc ... (sauf "noLines"). Il est ainsi possible d'imbriquer une infinité de sous-requêtes.
    * Le nom du champ JSON peut être formaté avec des "__" pour introduire plusieurs niveaux dans la hiérarchie d'objets.
    * Le Requêtage peut également être un tableau de Requêtage, auquel cas le champ JSON contiendra un tableaux de tous les résultats.
* "resultType" : Si le résultat attendu est une valeur ("value"), un objet unique ("object"), un tableau ("array") ou une liste ("list"). Liste  par défaut. Si "object" et plusieurs objet renvoyés par la requête, alors le premier est pris en compte, les autres sont ignorés. Si "value" et plusieurs colonnes, alors la première est prise en compte les autres sont ignorées. Si "array" et plusieurs colonnes, alors la première est prise en compte les autres sont ignorées.
* "noLines" : Si aucune ligne n'est retournée par la requête SQL, le statut, le message de statut HTTP et la réponse peuvent être surchargés via ce paramètre.
* "customParameters": Permet de créer de nouveaux paramètres, éventuellement dérivés des existants. Voir chapitre dédié.

"query" et "conditionQuery" ne peuvent pas être utilisés ensemble. Si "conditionQuery" est utilisé, la présence de "conditions" est obligatoire. Voir les exemple pour l'utilisation de condition complexes (>, <, etc).

Les noms des colonnes SQL ou des alias donnés lors du requêtage donne directement le nom du champ en JSON.
Exemple : `select "monChamp" from ma_table`
Résultat :

    {
      "data":[ {
        "monChamp" : "valeur 1"
      }, {
        "monChamp" : "valeur 2"
      } ]
    }

Ou : `select "monChamp" as "monAlias" from ma_table`
Résultat :

    {
      "data":[ {
        "monAlias" : "valeur 1"
      }, {
        "monAlias" : "valeur 2"
      } ]
    }

Il est possible d'obtenir des sous-objets en utilisant un double caractère "souligné" ("__") pour séparer les noms de champs JSon. Le nombre d'objets imbriqués n'est pas limité.
Exemple : `"query": "select "monChamp", "monAutreChamp1" as "autre__monChamp1", "monAutreChamp2" as "autre__monChamp2" from ma_table"`
Résultat :

    {
      "data":[ {
        "monChamp" : "valeur 1",
        "autre" : {
          "monChamp1" : "Valeur 1 - 1"
          "monChamp2" : "Valeur 1 - 2"
        }
      }, {
        "monChamp" : "valeur 2",
        "autre" : {
          "monChamp1" : "Valeur 2 - 1"
          "monChamp2" : "Valeur 2 - 2"
        }
      } ]
    }

Autre possibilité, utiliser les alias :
Exemple :

    "query": "select * from table1 t1 JOIN table2 t2 ON t1.\"code\" = t2.\"codeExterne\"",
    "aliases":  {
      "table2": {
        "prefix": "sousObjetTable2",
        "columns": {
          "code": "codeTable2"
        }
      }
    }

Résultat :

    {
      "data":[ {
        "code" : "Code Table 1",
        "autreChamp1" : "Autre champ table 1",
        "sousObjetTable2" : {
          "codeTable2" : "Code Table 2",
          "codeExterne" : "Code Table 1",
          "autreChamp2" : "Autre champ table 1"
        }
      } ]
    }


Enfin, une query pouvant éventuellement être longue et JSon ne supportant pas les retours à la ligne, la possibilité de déclarer la "query" sous forme de tableau de String a été donnée. Les différents "morceaux" seront concaténés avec un espace en séparateur pour l'exécution.

### Configuration globale

L'extention permet de fournir des paramètres globaux via un fichier de configuration. Ce fichier est nommé "WireMockCsv.json.conf" et est placé dans le répertoire "csv" (i.e. au même niveau que la base de données).
Il est en format JSON et sa structure est identique à celle du requêtage (i.e. du champ "transformerParameters"), sachant que dans les faits, seuls certains paramètres seront utiles à configurer à ce niveau (essentiellement "structure" et "no-line").
Les paramètres configurés au niveau global sont surchargeables unitairement pour chaque mapping.

Exemple de fichier de configuration :

    {
      "structure": {
        "data": "$WireMockCsv"
      },
      "noLines": {
        "status": 404,
        "statusMessage": "No data.",
        "response": {
          "message": "Pas de donnée trouvée."
        }
      }
    }

### Paramètres custom

Dans certains cas, les paramètres disponibles ne peuvent pas être directement intégrés dans les requêtes SQL. Par exemple, un tableau de valeurs ou une String contenant des valeurs délimitées et devant être intégrées dans une clause SQL IN.

Pour faire cette opération, il faut utiliser les paramètres custom, permettant de transformer un paramètre existant pour en obtenir un nouveau qu'il sera possible d'utiliser. Plusieurs opérations peuvent être chainées pour obtenir la transformation désirée.

Les paramètres custom peuvent être utilisés au niveau de la requête racine, ou dans des subqueries, auquel cas leur portée sera limitée à cette subquery et ses propres subqueries.

Syntaxe :

    "transformerParameters":{
      "customParameters": {
        "nom_premier_parametre": {
          "action": "nom-action",
          "param1": "valeur",
          "param2": "valeur"
        },
        "nom_second_parametre": {
          "action": "nom-action",
          "param1": "valeur",
          "param2": "valeur"
        }
      }
    }

Les opérations suivantes sont disponibles :
* "split": diviser une chaines de caractère en utilisant une expression régulière. Paramètres :
    * "action": "split".
    * "sourceParam": Le nom du paramètre contenant la valeur à splitter.
    * "regexp": L'expression régulière à utiliser pour splitter.
* "replace": remplace toutes les occurences identifiées par une expression régulière dans une String par une nouvelle valeur. Paramètres :
    * "action": "replace",
    * "sourceParam": Le nom du paramètre contenant la valeur sur laquelle appliquer le remplacement.
    * "regexp": L'expression régulière à utiliser pour identifier les occurences.
    * "replacement": La nouvelle valeur. Supporte les groupes de regexp ($0, $1, ...)
* "concatenate": concatène une ou plusieurs Strings avec un préfixe, un séparateur et un suffixe. Paramètres :
    * "action": "concatenate",
    * "sourceParam": Le nom du paramètre contenant les valeurs à concaténer.
    * "prefix"
    * "suffix"
    * "separator"
* "fromQuery": Utilise le résultat s'une requête SQL et le positionne dans un paramètre. Si la requête retourne plusieurs lignes ou plusieurs colonnes, le paramètre contiendra une liste. un résultat à plusieurs lignes et plusieurs colonnes ne sera pas géré. Paramètres :
    * "action": "fromQuery",
    * "query": LA requête à exécuter. Tous les paramètres existant y sont utilisables, y compris les custom déclarés avant.
* "escapeSql": Echappe les quotes (') dans une ou plusieurs Strings pour les utiliser dans des chaines de caractères SQL. Paramètres :
    * "action": "escapeSql",
    * "sourceParam": Le nom du paramètre contenant les valeurs à échapper.


Example :

    "transformerParameters":{
      "customParameters": {
        "tableau_codes_factures": {
          "action": "fromQuery",
          "query": "select code from facture where annee = ${anneesRecherchees}"
        },
        "string_codes_factures": {
          "action": "concatenate",
          "sourceParam": "tableau_codes_factures",
          "prefix": "Tous les codes de factures : [",
          "separator": ", "
          "suffix": "]",
        }
      },
      "query": "values('${string_codes_factures}')"
    }

### Exemple complet

    {
      "request": {
        "method": "GET",
        "urlPath": "/url",
        "queryParameters" : {
          "parametreHttp" : {
            "matches" : "..*"
          }
        }
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json; charset=utf-8" },
        "transformerParameters":{
          "query":"SELECT t.* FROM ma_table t WHERE t.\"code\" = '${parametreHttp}'",
          "subqueries": {
            "subListe1": {
              "query":"SELECT st.* FROM ma_sous_table1 st WHERE st.\"externalCode\" = '${code}'",
              "mask": ["champACacher"]
            },
            "subListe2": {
              "query":[
                "SELECT st.*, ast.\"monChamp\" AS \"sousObjet__monChamp\", ast.\"monAutreChamp\" AS \"sousObjet__monAutreChamp\"",
                "FROM ma_sous_table2 st JOIN autre_sous_table ast ON st.\"otherExternalCode\" = ast.\"code\"",
                "WHERE st.\"externalCode\" = '${code}'"],
              "subqueries": {
                "subSubListe": {
                  "query": "SELECT sst.* FROM ma_sous_sous_table sst WHERE sst.\"externalCode\" = '${code}'"
                }
              },
              "mask": ["externalCode", "autreChampACacher"]
            },
            "noLines": {
              "status": 404,
              "statusMessage": "No data.",
              "response": "No data."
            }
          }
        },
        "transformers": ["wiremock-csv"]
      }
    }

## URL des json de test:

Les URL ci-dessous sont valables si l'extension standalone a été lancée en local via l'un des deux moyens indiqués ci-dessus.

### Données de factures

Les exemples ci-dessous vont du plus simple au plus complexe. Le dernier utilise toutes les possibilités de l'extension en mixant les types de requêtage.
Les différentes requêtes SQL présentent quelques exemples d'astuces permettant de filtrer, de compter, de valider, etc ...
De plus, cet exemple utilise un fichier de configuration global permettant de changer la structure de la réponse et le comportement en cas de "pas de donnée".

* Recherche de clients avec ou sans filtre
    * http://localhost:8181/rechercherClients
    * http://localhost:8181/rechercherClients?filtrePrenom=Jo
    * http://localhost:8181/rechercherClients?filtrePrenom=Jo&filtreNom=Do

* Récupérer un client, avec agrégation de données
    * http://localhost:8181/recupererClient?clientCode=CLI01
    * http://localhost:8181/recupererClient?clientCode=CLI02
    * http://localhost:8181/recupererClient?clientCode=CLI03
    * http://localhost:8181/recupererClient?clientCode=CLI04

* Valider un code client
    * http://localhost:8181/validerClient?clientCode=CLI01
    * http://localhost:8181/validerClient?clientCode=CODE_BIDON

* Recherche d'articles avec ou sans filtre
    * http://localhost:8181/rechercherArticles
    * http://localhost:8181/rechercherArticles?filtreLibelle=Cla

* Recherche de factures avec ou sans filtre, avec agrégation de données et changement de structure de résultat. Deux syntaxes présentées
    * http://localhost:8181/rechercherFactures
    * http://localhost:8181/rechercherFactures?clientCode=CLI01
    * http://localhost:8181/rechercherFactures?clientCode=CLI02
    * http://localhost:8181/rechercherFactures2
    * http://localhost:8181/rechercherFactures2?clientCode=CLI01
    * http://localhost:8181/rechercherFactures2?clientCode=CLI02

* Récupérer une facture, avec sous-objet client. Deux syntaxes présentées.
    * http://localhost:8181/recupererFacture?factureCode=FAC01
    * http://localhost:8181/recupererFacture2?factureCode=FAC01

* Récupérer une ligne de facture, avec sous-objets article et facture avec lui-même le sous-sous-objet client
    * http://localhost:8181/recupererLigneFacture?ligneFactureCode=L01_01

* Récupérer une facture, avec sous-objet client et sous-liste de lignes, et gestion de l'absence de données
    * http://localhost:8181/recupererFactureAvecLignes?factureCode=FAC01
    * http://localhost:8181/recupererFactureAvecLignes?factureCode=NOT_EXISTING

* Recherche de factures contenant tous les articles d'une liste. Montre différentes syntaxes pour utiliser les paramètres custom
    * http://localhost:8181/rechercherFacturesSelonListeArticles1?articleCodes=ART01;ART03
    * http://localhost:8181/rechercherFacturesSelonListeArticles2?articleCodes=ART01;ART03
    * http://localhost:8181/rechercherFacturesSelonListeArticles3?articleCode=ART01&articleCode=ART03

* Récupérer un client avec un tableau de codes de factures ou un message si aucune facture (depuis 1.1.0)
    * http://localhost:8181/clientAvecListeCodeFactureOuMessage?clientCode=CLI01
    * http://localhost:8181/clientAvecListeCodeFactureOuMessage?clientCode=CLI04

* Récupérer un client avec un objet contenant des messages informatifs et warnings (depuis 1.1.0)
    * http://localhost:8181/clientAvecMessages?clientCode=CLI01
    * http://localhost:8181/clientAvecMessages?clientCode=CLI04

* Ces exemples montrent comment utiliser des conditions plus complexes. Le concept est simplement d'utiliser le SQL pour calculer les conditions (since 1.1.0)
    * http://localhost:8181/clientAvecMessage_conditionComplexe1?clientCode=CLI01
    * http://localhost:8181/clientAvecMessage_conditionComplexe2?clientCode=CLI01

* Récupérer un client avec sous-listes d'adresses et de factures, elle-même avec sous-liste de lignes, elle-même avec sous-objet article. Avec ou sans filtre. Deux syntaxes différentes présentées
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01&dateCommandeMin=2017-03-01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01&dateCommandeMin=2017-03-01

* Faire une extraction de données, en ne spécifiant que des subqueries
    * http://localhost:8181/extraction

* Autres examples pour montrer d'autres possibilités avec les paramètres custom
    * http://localhost:8181/testCustomParamFromQuery1
	* http://localhost:8181/testCustomParamFromQuery2
	* http://localhost:8181/testCustomParamFromQuery3
	* http://localhost:8181/testCustomParamInSubQuery (paramètres custom dans sub queries, depuis 1.1.0)

* Post Json Payload to wiremock to apply parameters from Json Request Body
curl -X POST --data '{ "request":[ {"customer":"4103446", "ExcludePromotion":"yes", "products":[{"product":"4099073", "quantity": 13}] } ] }' -H "Content-Type:application/json" http://localhost:8181/prices


## Changes history:

### 1.2.0

* Update to wiremock 3.13.1

### 1.1.2

* Feature: Utiliser le body Json de la requête http POST et en extraite les paramètre pour utilisation dans les requêtes SQL.

### 1.1.1

* Code cleaning
* Better handling of special characters
* (Fix) configuration retrieval fails if not launched via integrated runner

### 1.1.0

* Custom parameters can now be used in sub queries
* New requests features
	* Conditional queries
	* Possibility to have arrays of sub-queries
* Handling new "array" type to generate arrays of values from SQL results (first column only)
* New examples

### 1.0.0

First release