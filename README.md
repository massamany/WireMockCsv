# Wiremock CSV Extension

*English version first. French version follows.*

## Introduction
The WireMock extension will allow you to read data from CSV with SQL queries, in order to automatically generate response JSON from this data.

## Build the application:
    mvn clean install
The "target" directory will then contain a standalone jar file with all necessary binaries.

## Launch in a standalone mode with console:
    java -jar ".\WiremockCsv-1.0-standalone.jar" -Dfile.encoding=UTF-8 --port 8181 --root-dir "###MY_PROJECT_PATH###\src\test\resources\mock"

## Launch in a standalone mode with Eclipse:
Use the provided launch configuration, eventually changing the root directory.

## Usage:
### Create your DB
It's a good idea to start from the provided test data. It's a good example.
To create you CSV database, follow these steps:
* Take the Database.properties and Database.script files from the examples.
* Create your CSV (one file per table, without header)
* Create the lines "CREATE TEXT TABLE PUBLIC.XXX" et "SET TABLE PUBLIC.XXX SOURCE 'xxx.csv;encoding=UTF-8;fs=\semi'" in Database.script, for all CSV. Columns names must be surrounded by double quotes, this is the key element for the field mappings to be correct.

That's it ! :-)

### Create your CSV mapping
*Note: it remains possible to use standard WireMock mappings and files in addition to specific CSV transformer files.*

The concept is simple: interrogate the CSV DB in SQL and map results to JSON response using SQL column names as JSON field names.

The standard "jsonBody" is replaced with 2 values:
* "transformers": ["wiremock-csv"]
* "transformerParameters": { Structure, Request }

The structure allows defining expected result structure and where will be integrated the request result. The result place is represented by a parameter named "${WireMockCsv}".
Default structure is `${WireMockCsv}`, to simply output the JSONified result.
Structure change example:

    "structure": {
      "main": "${WireMockCsv}"
    },


The request allows:
* Listing a table lines, with one or more filters from HTTP parameters.
* Performing joins, subselects.
* Obtaining a complex Object hierarchy as a result, with sub-objects and sub-lists.

### Building the request
A request is composed by several components:
* "query": A SQL request, potentially parameterized with HTTP request parameters or "mother" query results (for sub-queries):
    * A parameter is declared as follows: ${myParameter}
    * Main query: It will be replaced with the value of the HTTP parameter with the same name.
    * Sub-query: It will be replaced with the value of the column with the same name, or if not found with the value of the HTTP parameter with the same name.
    * A parameter with no replacement values will be replaced by an empty String.
* "mask": A list of column names of the query results which will not appear in the generated JSON. This allows retrieving values to use as parameters for sub-requests. Other trick, use "select*" and mask one column instead of listing all needed columns, the request will be sorter.
* "aliases": Alternative to parameter sub-objects and fields names, instead of using columns names.
* "subqueries": It's simply a list of Map [String ; Request]. The key is the JSON field name of the future sub-list or sub-object to retrieve and the request is again all of the components "query", "subqueries", etc ... (except "no-lines). It's hence possible to interlock an infinite number of sub-requests.
* "resultType": Tells if the expected result is a value ("value"), a unique object ("object") or a list ("list"). List by default. If "object" and several objects returned by the query, then only the first one is taken into account, the others are ignored. If "value" and several columns, then only the first one is taken into account, the others are ignored.
* "no-lines": If no lines are returned by the SQL query, the HTTP status, status message and response can be overriden with this parameter.

The SQL columns names or the SQL alias given while querying provide directly the JSON field name.
Example: `select "myField" from my_table`
Result:

    {
      "data":[ {
        "myField" : "Value 1"
      }, {
        "myField" : "Value 2"
      } ]
    }

Or: `select "myField" as "myAlias" from my_table`
Result:

    {
      "data":[ {
        "myAlias" : "Value 1"
      }, {
        "myAlias" : "Value 2"
      } ]
    }

It is possible to obtain sub-objects by using a double underscore ("__") to separate the names of the JSON fields. There are no limits on the number of levels in the hierarchy.
Example: `"query": "select "myField", "myOtherField1" as "other__myField1", "myOtherField2" as "other__myField2" from ma_table"`
Result:

    {
      "data":[ {
        "myField" : "valeur 1",
        "other" : {
          "myField1" : "Value 1 - 1"
          "myField2" : "Value 1 - 2"
        }
      }, {
        "myField" : "valeur 2",
        "other" : {
          "myField1" : "Value 2 - 1"
          "myField2" : "Value 2 - 2"
        }
      } ]
    }

Other possibility, using aliases:
Example:

    "query": "select * from table1 t1 JOIN table2 t2 ON t1.\"code\" = t2.\"externalCode\"",
    "aliases":  {
      "table2": {
        "prefix": "subObjectTable2",
        "columns": {
          "code": "codeTable2"
        }
      }
    }

Result:

    {
      "data":[ {
        "code" : "Code Table 1",
        "otherField1" : "Other field table 1",
        "subObjectTable2" : {
          "codeTable2" : "Code Table 2",
          "externalCode" : "Code Table 1",
          "otherField2" : "Other field table 1"
        }
      } ]
    }
   
Finally, a SQL query can be long and difficult to read if on one line. JSon does not support line feeds, so the possibility to declare the query as an array of Strings has been given. All blocks will be concatenated with a space separator to execute the whole query.


### Global configuration

The extention allows providing global parameters via a configuration file. This file is named "WireMockCsv.json.conf" and is placed in the "csv" directory (i.e. same level than the database).
It contains JSON Data and its structure is the same than the "transformerParameters" field, knowing that in the real life, only few parameters will be useful to configure here  (minly "structure" and "no-line").
Global parameters may be overriden individually for each mapping.

Configuration file example:

    {
      "structure": {
        "data": "$WireMockCsv"
      },
      "no-lines": {
        "status": 404,
        "statusMessage": "No data.",
        "response": {
          "message": "No data found."
        }
      }
    }

### Full example

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
          "query":"SELECT t.* FROM my_table t WHERE t.\"code\" = '${httpParameter}'",
          "subqueries": {
            "subListe1": {
              "query":"SELECT st.* FROM my_sub_table1 st WHERE st.\"externalCode\" = '${code}'",
              "mask": ["fieldToHide"]
            },
            "subListe2": {
              "query":[
                "SELECT st.*, ast.\"myField\" AS \"subObjet__myField\", ast.\"myOtherField\" AS \"sousObjet__myOtherField\"",
                "FROM my_sub_table2 st JOIN other_sub_table ast ON st.\"otherExternalCode\" = ast.\"code\"",
                "WHERE st.\"externalCode\" = '${code}'"],
              "subqueries": {
                "subSubListe": {
                  "query": "SELECT sst.* FROM my_sub_sub_table sst WHERE sst.\"externalCode\" = '${code}'"
                }
              },
              "mask": ["externalCode", "otherFieldToHide"]
            },
            "no-lines": {
              "status": 404,
              "statusMessage": "No data.",
              "response": "No data."
            }
          }
        },
        "transformers": ["wiremock-csv"]
      }
    }


## Test JSON URL:

Following URL are valid if standalone extension has been launched locally via one of the two way exposed above.

### Invoicing data

Following examples goes on from the simplest to the most complex. The last one uses all extension features, by mixing requests types.
The different SQL queries present some examples allowing filtering, count, validate, etc...
In addition, this example uses a global configuration file allowing to change the structure of the responses and the "no-line" behavior.

* Search clients with or without filtering
    * http://localhost:8181/rechercherClients
    * http://localhost:8181/rechercherClients?filtreNom=Jo
    * http://localhost:8181/rechercherClients?filtreNom=Jo&filtrePrenom=Do
   
* Retrieve a client, with data aggregation
    * http://localhost:8181/recupererClient?clientCode=CLI01
    * http://localhost:8181/recupererClient?clientCode=CLI02
    * http://localhost:8181/recupererClient?clientCode=CLI03
    * http://localhost:8181/recupererClient?clientCode=CLI04

* Validate client code
    * http://localhost:8181/validerClient?clientCode=CLI01
    * http://localhost:8181/validerClient?clientCode=CODE_BIDON

* Search articles with or without filtering
    * http://localhost:8181/rechercherArticles
    * http://localhost:8181/rechercherArticles?filtreLibelle=Cla

* Search invoices with or without filtering, with data aggregation and result structure changes. Two different syntax demonstrated
    * http://localhost:8181/rechercherFactures
    * http://localhost:8181/rechercherFactures?clientCode=CLI01
    * http://localhost:8181/rechercherFactures?clientCode=CLI02
    * http://localhost:8181/rechercherFactures2
    * http://localhost:8181/rechercherFactures2?clientCode=CLI01
    * http://localhost:8181/rechercherFactures2?clientCode=CLI02

* Retrieve an invoice, with client sub-object. Two different syntax demonstrated
    * http://localhost:8181/recupererFacture?factureCode=FAC01
    * http://localhost:8181/recupererFacture2?factureCode=FAC01

* Retrieve an invoice line, with article and invoice sub-objects and with client sub-sub-object for the invoice
    * http://localhost:8181/recupererLigneFacture?ligneFactureCode=L01_01

* Retrieve an invoice, with client sub-object and sub-list of lines, and handling the absence of data
    * http://localhost:8181/recupererFactureAvecLignes?factureCode=FAC01
    * http://localhost:8181/recupererFactureAvecLignes?factureCode=NOT_EXISTING

* Retrieve a client with sub-lists of addresses and invoices, each invoice with a sub-list of lines, each line with an article sub-object. With or without filtering. Two different syntax demonstrated
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01&dateCommandeMin=2017-03-01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01&dateCommandeMin=2017-03-01

* Extract data, by specifying only sub-queries
    * http://localhost:8181/extraction



# Français - Extension Wiremock CSV

## Construire le livrable:
    mvn clean install
Le répertoire "target" contiendra alors un jar standalone contenant l'intégralité des binaires nécessaires.
## Lancer l'application en standalone via console:
    java -jar ".\WiremockCsv-1.0-standalone.jar" -Dfile.encoding=UTF-8 --port 8181 --root-dir "###MY_PROJECT_PATH###\src\test\resources\mock"

## Lancer l'application en standalone via Eclipse:
Utiliser la configuration de lancement fournie, éventuellement en changeant le répertoire root-dir.

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
    * Un paramètre se déclare comme suit : ${monParamètre}
    * Requête principale : Il sera remplacé par la valeur du paramètre HTTP du même nom
    * Sous requête : Il sera remplacé par la valeur de la colonne du même nom ou à défaut par la valeur du paramètre HTTP du même nom
    * Un paramètre n'ayant pas de valeur de remplacement sera remplacé par une chaîne vide
* "mask" : Une liste des noms de colonnes résultats de la requête principale qui n'apparaîtront pas dans le JSon final, ceci permettant de récupérer des valeurs pour un paramètre de sous-requête. Autre avantage, utiliser le "select *" et supprimer une colonne du résultat afin d'obtenir requête moins verbeuse.
* "aliases" : Alternative pour paramétrer les noms de sous-objets et de champs, évitant d'utiliser les alias de nom colonne.
* "subqueries" : Il s'agit en fait simplement d'une Liste de Map de [String ; Requêtage]. La clé est le nom (champ JSon) de la sous-liste à récupérer et le requêtage est encore une fois un ensemble des composants "query", "subqueries", etc ... (sauf "no-lines"). Il est ainsi possible d'imbriquer une infinité de sous-requêtes.
* "resultType" : Si le résultat attendu est une valeur ("value"), un objet unique ("object") ou une liste ("list"). Liste  par défaut. Si "object" et plusieurs objet renvoyés par la requête, alors le premier est pris en compte, les autres sont ignorés. Si "value" et plusieurs colonnes, alors la première est prise en compte les autres sont ignorées.
* "no-lines" : Si aucune ligne n'est retournée par la requête SQL, le statut, le message de statut HTTP et la réponse peuvent être surchargés via ce paramètre.

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
      "no-lines": {
        "status": 404,
        "statusMessage": "No data.",
        "response": {
          "message": "Pas de donnée trouvée."
        }
      }
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
            "no-lines": {
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
    * http://localhost:8181/rechercherClients?filtreNom=Jo
    * http://localhost:8181/rechercherClients?filtreNom=Jo&filtrePrenom=Do

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

* Récupérer un client avec sous-listes d'adresses et de factures, elle-même avec sous-liste de lignes, elle-même avec sous-objet article. Avec ou sans filtre. Deux syntaxes différentes présentées
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle?clientCode=CLI01&dateCommandeMin=2017-03-01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01
    * http://localhost:8181/recupererClientAvecAdressesEtFacturesAvecLignesAvecArticle2?clientCode=CLI01&dateCommandeMin=2017-03-01

* Faire une extraction de données, en ne spécifiant que des subqueries
    * http://localhost:8181/extraction
    