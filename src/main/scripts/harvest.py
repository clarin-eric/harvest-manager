#!/usr/bin/env python

from plumbum import local, cli, FG, BG, TF
from plumbum.path.utils import move, delete
import datetime
import os
import sys
import re
import urlparse

class OaiHarvestError(StandardError):
    def __init__(self, message, args=[]):
        self.message = message
        self.args = args

class OaiHarvest:

    def __init__(self, conf=None, dry=None, oai="/app/oai", base="/app/workdir", output="test", name="test", jvm="-Xmx1G", postgres="oai:oai@localhost:5432/oai", verbose=False):
        self.verbose = verbose

        self.oai = oai
        self.base = base

        self.name = name
        self.output = output

        self.confdir = conf

        self.jvm = jvm

        self.dry = dry

        self.pg = False
        if postgres:
            self.pg = True
            pgg = re.search("(.*):(.*)@(.*):(.*)/(.*)",postgres)
            self.pg_user = pgg.group(1)
            self.pg_pass = pgg.group(2)
            self.pg_host = pgg.group(3)
            self.pg_port = pgg.group(4)
            self.pg_db   = pgg.group(5)
        
        self.find = local["find"]
        self.rsync = local["rsync"]
        self.tar = local["tar"]
        self.bzip2 = local["bzip2"]
        if self.pg:
            self.psql = local["psql"]

        self.harvester = local[os.path.join(oai, "run-harvester.sh")]
        self.viewer = local[os.path.join(oai, "run-viewer.sh")]
        self.workdir = os.path.join(base, "workdir", "%s-%s" % (output, name))
        self.logdir = os.path.join(self.workdir, "log")
        self.tempdir = os.path.join(base, "tmp", "zzz-%s-%s" % (output, name))
        if not conf:
            self.confdir = os.path.join(oai, "resources")
        self.outputdir = os.path.join(base, "output", output)
        self.logsdir = os.path.join(self.outputdir, "log")
        self.resultdir = os.path.join(base, "resultsets")
        self.backupdir = os.path.join(self.resultdir, "backups")
        self.config_file = "config-%s-%s.xml" % (output, name)

        if self.verbose:
            self.print_to_stdout("Config:\n")
            self.print_to_stdout("\tdry run: %s\n" % self.dry)
            self.print_to_stdout("\tconf dir: %s\n" % self.confdir)
            self.print_to_stdout("\tconf file: %s\n" % self.config_file)
            self.print_to_stdout("\tlog dir: %s\n" % self.logdir)
            self.print_to_stdout("\tlogs dir: %s\n" % self.logsdir)
            self.print_to_stdout("\twork dir: %s\n" % self.workdir)
            self.print_to_stdout("\ttemp dir: %s\n" % self.tempdir)
            self.print_to_stdout("\toutput dir: %s\n" % self.outputdir)
            self.print_to_stdout("\tresults dir: %s\n" % self.resultdir)
            self.print_to_stdout("\tbackup dir: %s\n" % self.backupdir)
            if self.pg:
                self.print_to_stdout("\tview db: %s/%s\n" % (self.pg_host,self.pg_db))

    def run(self):
        self.print_to_stdout("Harvest run started, output=%s, name=%s.\n" % (self.output, self.name))
        self.print_to_stdout("\tInitializing.\n")
        self.initialize()
        self.print_to_stdout("\tDone\n")

        self.print_to_stdout("\tRunning harvester.\n")
        self.run_harvest()
        self.print_to_stdout("\tDone\n")

        self.print_to_stdout("\tReset output.\n")
        self.do_reset()
        self.print_to_stdout("\tDone\n")

        self.print_to_stdout("\tMerging harvest result to output.\n")
        self.merge("results/cmdi")
        self.merge("results/cmdi-1_1")
        self.merge("oai-pmh")
        # TODO merge map.csv
        move(os.path.join(self.workdir, "map.csv"), os.path.join(self.outputdir, "results", "map.csv"))
        self.merge_logs()
        self.print_to_stdout("\tDone\n")

        self.print_to_stdout("\tBackup harvest result.\n")
        self.run_backup()
        self.print_to_stdout("\tDone\n")

        move(os.path.join(self.outputdir, "results", "map.csv"), os.path.join(self.workdir, "map.csv"))
        self.print_to_stdout("\tGenerate update for the harvest view database.\n")
        self.run_viewer()
        self.print_to_stdout("\tDone\n")
        if self.pg: 
            self.print_to_stdout("\tUpdating harvest view database.\n")
            self.run_psql()
            self.print_to_stdout("\tDone\n")
        move(os.path.join(self.workdir, "map.csv"), os.path.join(self.outputdir, "results", "map.csv"))        

        self.print_to_stdout("\tCleanup.\n")
        self.do_cleanup()
        self.print_to_stdout("\tDone\n")

        self.print_to_stdout("Done\n")

    def initialize(self):
        """
        Make sure that:
        - all required directories exist (create if needed)
        - all required files exist (throw error if they don't exist)
        """

        online = re.match("http(s)?://.*",self.confdir)
        if not online:
            #Ensure conf dir and file exist
            if not os.path.exists(self.confdir):
                raise OaiHarvestError("Config dir [%s] not found" % self.confdir)

            absolute_config_file = os.path.join(self.confdir, self.config_file)
            if not os.path.isfile(absolute_config_file):
                raise OaiHarvestError("Config file [%s] not found" % absolute_config_file)

        #Clean workdir
        if os.path.exists(self.workdir):
            delete(self.workdir)

        #Ensure directories exist
        self.make_dir(self.base, self.workdir, self.outputdir, self.resultdir, self.backupdir, self.logdir, self.logsdir, self.tempdir)

    def run_harvest(self):
        """
        Run the harvester
        """
        local.env["LOG_DIR"] = self.logdir
        local.env["PROPS"] = self.jvm
        conf = None
        if re.match("http(s)?://.*",self.confdir):
            conf = urlparse.urljoin(self.confdir, self.config_file)
        else:
            conf = os.path.join(self.confdir, self.config_file)
        command = [
            "workdir=%s" % self.workdir,
            "overview-file=%s" % os.path.join(self.workdir, "overview.xml"),
            "map-file=%s" % os.path.join(self.workdir, "map.csv"),
            conf
        ]
        if self.dry == True:
            command.insert(0,"dry-run=true")

        if self.verbose:
            self.print_to_stdout("\t\tHarvester command:\n")
            self.print_to_stdout("\t\t\t%s " % self.harvester)
            for i in command:
                self.print_to_stdout("%s " % i)
            self.print_to_stdout("\n")

        return self.harvester(command)

    def do_reset(self):
        """
        start with a fresh output
        """
        if (self.output == self.name):

            if self.verbose:
                self.print_to_stdout("\t\tReset output: %s\n" % self.outputdir)

            delete(self.tempdir)
            move(self.outputdir, os.path.join(self.tempdir, "output"))
            self.make_dir(self.outputdir)
            self.make_dir(self.logsdir)

    def do_cleanup(self):
        """
        cleanup
        """
        delete(self.tempdir)

    def merge(self, dir):
        """
        Merge the harvest result from the output directory into the output directory
        """
        search_directory=os.path.join(self.workdir, dir)

        if not os.path.isdir(search_directory):
            print "Directory doesn't exist: %s" % search_directory
            return None

        destination = search_directory.replace(self.workdir, self.outputdir)

        self.print_to_stdout("\t\tDirectory: %s -> %s\n" % (search_directory,destination))
        list = self.search(search_directory)
        for line in list:
            source = line.strip()
            self.print_to_stdout("\t\t\t%s\n" % source)
            self.do_rsync(source, destination)

    def merge_logs(self):
        """
        save the logs
        """
        for log in local.path(self.logdir).list():
            move(log, os.path.join(self.logsdir,log.name))

    def run_backup(self):
        """
        Make a backup
        """
        ball = "%s.tar.bz2" % self.output

        with local.cwd(self.outputdir):
            chain = ((self.tar["cf", "-", "results", "log"] | self.bzip2) > ball)

            if self.verbose:
                self.print_to_stdout("\t\tbackup command:\n")
                self.print_to_stdout("\t\t\t%s " % chain)
                self.print_to_stdout("\n")

            chain()
        
        if os.path.exists(os.path.join(self.resultdir, ball)):
            now = datetime.datetime.now()
            backup = "%s.%s-%s-%s.tar.bz2" % (self.output, now.year, now.month, now.day)
            move(os.path.join(self.resultdir, ball), os.path.join(self.backupdir, backup))
            if self.verbose:
                self.print_to_stdout("\t\tbackup previous run:\n")
                self.print_to_stdout("\t\t\t%s " % backup)
                self.print_to_stdout("\n")

        move(os.path.join(self.outputdir, ball), os.path.join(self.resultdir, ball))

    def run_viewer(self):
        """
        Run the viewer
        """
        command = [
            self.output,
            self.outputdir,
            os.path.join(self.workdir, "viewer.sql")
        ]

        if self.verbose:
            self.print_to_stdout("\t\tViewer command:\n")
            self.print_to_stdout("\t\t\t%s " % self.viewer)
            for i in command:
                self.print_to_stdout("%s " % i)
            self.print_to_stdout("\n")

        return self.viewer(command)

    def run_psql(self):
        """
        Run the psql
        """
        local.env["PGPASSWORD"] = self.pg_pass

        now = datetime.datetime.now()
        #backup = "%s.%s-%s-%s-%s-%s.tar.bz2" % (self.output, now.year, now.month, now.day, now.hour, now.minute)
        backup = "%s.%s-%s-%s.tar.bz2" % (self.output, now.year, now.month, now.day)
        command = ["-c", "UPDATE harvest SET location = '%s' WHERE \"type\" = '%s' AND location = '%s';" % (backup, self.output, self.outputdir),
            "-U", self.pg_user,
            "-h", self.pg_host,
            "-p", self.pg_port,
            self.pg_db]

        if self.verbose:
            self.print_to_stdout("\t\tPSQL command:\n")
            self.print_to_stdout("\t\t\t%s " % self.psql)
            for i in command:
                self.print_to_stdout("%s " % i)
            self.print_to_stdout("\n")

        self.psql(command)

        log = os.path.join(self.workdir, "viewer.sql.log")
        chain = (self.psql["-f", os.path.join(self.workdir, "viewer.sql"),
            "-U", self.pg_user,
            "-h", self.pg_host,
            "-p", self.pg_port,
            self.pg_db] > log)

        if self.verbose:
            self.print_to_stdout("\t\tPSQL command:\n")
            self.print_to_stdout("\t\t\t%s " % chain)
            self.print_to_stdout("\n")

        chain()

    def search(self, directory):
        """
        Search all leaf directories in the specified directory
        """
        result = self.find(directory, "-mindepth", "1", "-type", "d")
        return result.splitlines()

    def do_rsync(self, source, destination):
        """
        Rsync source to destination
        """
        self.make_dir(destination)
        return self.rsync("-ahrv", "-delete", source, destination)

    def make_dir(self, *dirs):
        """
        Make all directories, including subdirectories, if they don't exist
        """
        for dir in dirs:
            if not os.path.isdir(dir):
                os.makedirs(name=dir)

    def print_to_stdout(self, text):
        """
        Print a message to stdout
        """
        sys.stdout.write(text)
        sys.stdout.flush()

class App(cli.Application):
    PROGNAME = "oai-harvest.py"
    VERSION = "0.0.1"
    verbose = cli.Flag(["v", "verbose"], help="Verbose output")
    confdir = None
    dry = None
    output = None
    name = None
    postgres = None

    @cli.switch(["-c", "--config"], str, mandatory=False, help="Config directory (can be online). (optional)")
    def set_config(self, config):
        if config:
            self.confdir = config

    @cli.switch(["-d", "--dry"], str, mandatory=False, help="Dry run. (optional)")
    def set_dry(self, dry):
        if dry:
            self.dry = True

    @cli.switch(["-o", "--output"], str, mandatory=True, help="Output folder (collection) this harvest is part of.")
    def set_output(self, output):
        if output:
            self.output = output

    @cli.switch(["-n", "--name"], str, mandatory=True, help="Name for this harvest run.")
    def set_name(self, name):
        if name:
            self.name = name

    @cli.switch(["-p", "--postgres"], str, mandatory=False, help="Postgres database (<user>:<pass>@<host>:<port>/<db>) to connext to. (optional)")
    def set_postgres(self, postgres):
        if postgres:
            self.postgres = postgres

    def main(self):
        oai = OaiHarvest(conf=self.confdir, dry=self.dry, output=self.output, name=self.name, postgres=self.postgres, verbose=self.verbose)
        try:
            oai.run()
        except Exception as e:
            sys.stdout.write(str(e))
            sys.stdout.flush()
            sys.exit(1)
        except:
            e = sys.exc_info()[0]
            print e
            sys.exit(1)
        else:
            sys.exit(0)

if __name__ == "__main__":
    App.run()