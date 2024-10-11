Étapes de mise en place une instace keycloak en local.
1)  lancer le sh clean-postgres.sh pour supprimer les données de la base de données postgres
2) lancer le sh clean-keycloak.sh pour supprimer les données de keycloak
3) lancer sh start-postgres.sh pour lancer la base de données postgres
4) lancer sh start-keycloak.sh pour lancer keycloak
5) lancer le sh import-realm.sh pour importer le realm dans keycloak
6) Accéder à l'interface de keycloak à l'adresse http://localhost:8089/auth
7) Se connecter avec l'utilisateur admin et le mot de passe admin
8) Aller sur la configuration User federation changer le mot de passe
9) Synchronez les utilisateurs